package com.thaze.peakmatch;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * 
 * steps:
 * 	1 process sample directory with full FFT xcorr process, store results to disk
 * 	2 parameter refine process
 * 		- run peak-matched sampled brute force (PMxcorr) xcorr across samples
 * 		- emit recall / precision compared to full FFT across samples
 * 		- emit time estimate for full data set 
 * 		- iterate until happy with quality / time tradeoff
 * 	3 full data set PMxcorr, store results
 * 	4 results of PMxcorr (<< full data set) -> full FFT xcorr, store final results 
 * 
 * @author srodgers
 *
 */
public class XCorrProcessor {
	
	private static final String CONF_FILE = "xcorr.conf";
	private static final String XCORR_SAVE_FILE = "xcorr.saved";
	private final EventProcessorConf _conf;
	
	public XCorrProcessor() throws EventException {
		_conf = EventProcessorConf.newBuilder(getProps()).build();
	}

	public static void main(String[] args) {
		
		System.out.println();
		
		long t0 = System.currentTimeMillis();
		System.out.println("*** Peak-Matched Sampled Brute Force X-Correlation ***");
		
		try{
			XCorrProcessor p = new XCorrProcessor();
			
			System.out.println("read " + CONF_FILE + " ...");
			System.out.println(p._conf);
			
			switch (p._conf.getMode()){
			case ANALYSE:
				p.analyseAccuracy();
				p.analysePerformance();
			break; case PEAKMATCH:
				// TODO
			break; case POSTPROCESS:
				// TODO
			}
		} catch (EventException e){
			System.err.println("error: " + e.getMessage());
			if (null != e.getCause())
				e.printStackTrace();
		}
		
		System.out.println("*** done [" + (System.currentTimeMillis()-t0) + " ms] ***");
	}
	
	private static Properties getProps() throws EventException{

		File propsFile = new File(CONF_FILE);
		if (!propsFile.exists())
			throw new EventException("missing conf file " + CONF_FILE);
		
		Properties props = new Properties();
		try {
			props.load(new FileReader(propsFile));
		} catch (IOException e) {
			throw new EventException(e);
		}
		return props;
	}

	private void analyseAccuracy() throws EventException {
		List<FFTPreprocessedEvent> events = loadSampleEvents();
		System.out.println("found " + countAllEvents() + " full events");
		
		Map<String, Double> full = loadSampleXCorr(events);
		Map<String, Double> fullAboveThreshold = reduceToFinalThreshold(full);
		
		Map<String, Double> rejections = Maps.newHashMap();
		Map<String, Double> candidates = generateCandidateXcorr(events, rejections);
		
		System.out.println();
		System.out.println("*** Accuracy analysis ***");
		System.out.println(events.size() + " events sampled -> " + events.size()*events.size()/2 + " distinct pairs");
		System.out.println(fullAboveThreshold.size() + " full XCorr event pairs above final threshold");
		System.out.println(candidates.size() + " candidates found above candidate threshold");
		
		if (fullAboveThreshold.isEmpty())
			System.out.println("no matches found in full xcorr");
		else {
		
			System.out.println();
			System.out.println("===  false positives ===");
			
			Set<String> falsePositives = Sets.newHashSet(candidates.keySet());
			falsePositives.removeAll(fullAboveThreshold.keySet());
			System.out.println(falsePositives.size() + " (" + 100*falsePositives.size()/fullAboveThreshold.size() + "% of full) false positives (higher = more post-process required)");
			
			if (_conf.isVerbose()){
				for (String fp: falsePositives)
					System.out.println(fp + "\t candidate xcorr: " + Util.NF.format(candidates.get(fp)) + ", real xcorr: " + full.get(fp));
			}

			System.out.println();
			System.out.println("===  false negatives ===");
			
			Set<String> falseNegatives = Sets.newHashSet(fullAboveThreshold.keySet());
			falseNegatives.removeAll(candidates.keySet());
			
			System.out.println(falseNegatives.size() + " (" + 100*falseNegatives.size()/fullAboveThreshold.size() + "% of full) false negatives (higher = more missed events)");
			if (_conf.isVerbose()){
				for (String fn: falseNegatives)
					System.out.println(fn + "\t real xcorr: " + fullAboveThreshold.get(fn) + ", candidate xcorr: " + rejections.get(fn));
			}
		}

		System.out.println();
		System.out.println("*** Accuracy analysis completed ***");
	}

	private void analysePerformance() throws EventException {
		List<FFTPreprocessedEvent> events = loadSampleEvents();
		
		System.out.println();
		System.out.println("*** Performance analysis ***");
		
		Map<String, Double> candidates;
		
		final int samplePairs = events.size()*events.size()/2;
		final long allPairs = countAllEvents()*countAllEvents()/2;
		
		long extrapolatedForAllMS;
		
		{
			System.out.println();
			System.out.println("=== Peakmatch phase ===");
			
			long t0 = System.currentTimeMillis();
			candidates = generateCandidateXcorr(events, null);
			long tPM = System.currentTimeMillis()-t0;
			
			int pairs = events.size()*events.size()/2;
			long eachMicrosec = 1000*tPM/pairs;
			long perSec = 1000000 / eachMicrosec;
			extrapolatedForAllMS = allPairs * eachMicrosec / 1000;
			
			System.out.println(events.size() + " events sampled -> " + pairs + " distinct pairs");
			System.out.println(tPM + " ms, " + eachMicrosec + "μs each, " + perSec + "/sec");
			
			System.out.println("PMSBF method - extrapolation to all events (" + allPairs + " distinct pairs of " + countAllEvents() + " events): " + Util.periodToString(extrapolatedForAllMS));
		}
		
		{
			System.out.println();
			System.out.println("=== Postprocess phase ===");
		
			long t0 = System.currentTimeMillis();
			Map<String, Double> finalMatches = fullXCorrPostProcess(candidates, events);
			long tPostProcess = System.currentTimeMillis()-t0;
			
			long eachMicrosec = 1000*tPostProcess/candidates.size();
			long perSec = 1000000 / eachMicrosec;
			long multiple = samplePairs / candidates.size();
			long extrapolatedNaiveBFForAllMS = allPairs * eachMicrosec / 1000;
			long extrapolatedPMForAllMS = extrapolatedNaiveBFForAllMS / multiple;
			
			System.out.println(candidates.size() + " pairs post-processed with full FFT xcorr -> " + finalMatches.size() + " final matches");
			System.out.println("1 / " + multiple + " of entire eventpair space necessary to full xcorr");
			System.out.println(tPostProcess + " ms, " + eachMicrosec + "μs each, " + perSec + "/sec");
			System.out.println("extrapolation to all events (" + allPairs + " distinct pairs of " + countAllEvents() + " events): " + Util.periodToString(extrapolatedPMForAllMS));
			
			long totalPMExtrapolation = extrapolatedPMForAllMS + extrapolatedForAllMS;
			
			System.out.println();
			System.out.println("total PM + postprocess extrapolation: " + Util.periodToString(totalPMExtrapolation));
			
			System.out.println();
			System.out.println("full naive brute force for comparison - extrapolation to all events: " + Util.periodToString(extrapolatedNaiveBFForAllMS));
		}
		
		
		
		System.out.println();
		System.out.println("*** Performance analysis completed ***");
	}
	
	private Map<String, Double> fullXCorrPostProcess(Map<String, Double> candidates, List<FFTPreprocessedEvent> events) {
	
		Map<String, Double> finalMatches = Maps.newHashMap();
		
		for (int ii = 0; ii < events.size(); ii++) {
			final FFTPreprocessedEvent a = events.get(ii);

			for (int jj = ii+1; jj < events.size(); jj++) {
				final FFTPreprocessedEvent b = events.get(jj);
				
				String key = new EventPair(a, b).key; 
				if (!candidates.containsKey(key))
					continue;
				
				double[] xcorr = Util.fftXCorr(a, b);
				double best = Util.getHighest(xcorr);
				
				if (best > _conf.getFinalThreshold())
					finalMatches.put(key, best);
			}
		}
		
		return finalMatches;
	}

	// peak-matching sampled brute force xcorrelation estimation
	private Map<String, Double> generateCandidateXcorr(List<? extends Event> events, Map<String, Double> rejections) {
		
		Map<String, Double> candidates = Maps.newHashMap();
		
		for (int ii = 0; ii < events.size(); ii++) {
			final Event a = events.get(ii);

			for (int jj = ii+1; jj < events.size(); jj++) {
				final Event b = events.get(jj);
				
				double bestPositivePeakMatchXCorr = xcorrSpecificOffset(a, b, a.getMaxSpatialPeaks(), b.getMaxSpatialPeaks());
				double bestNegativePeakMatchXCorr = xcorrSpecificOffset(a, b, a.getMinSpatialPeaks(), b.getMinSpatialPeaks());
				
				double best = Math.max(bestPositivePeakMatchXCorr, bestNegativePeakMatchXCorr);
				
				if (best > _conf.getCandidateThreshold()) 
					candidates.put(new EventPair(a, b).key, best);
				else if (null != rejections) 
					rejections.put(new EventPair(a, b).key, best);
			}
		}
		return candidates;
	}
	

	private Map<String, Double> reduceToFinalThreshold(Map<String, Double> fullXcorr) {
		return Maps.filterValues(fullXcorr, new Predicate<Double>() {
			@Override
			public boolean apply(Double value) {
				return value >= _conf.getFinalThreshold();
			}
		});
	}
	
	private double xcorrSpecificOffset(Event a, Event b, int[] aOffsets, int[] bOffsets) {
		final int len = a.getD().length;
		final int samplingStride = _conf.getSamplingStride();
		double bestXcorr = -1;

		for (int aOffset : aOffsets) {
			for (int bOffset : bOffsets) {

				final int offset = aOffset - bOffset;
				final int start = offset < 0 ? -offset : 0;
				final int end = offset < 0 ? len : len - offset;

				double d = 0;
				final int[] bIndexes = b.getIndexesAboveThreshold();
				final int bIndexesLength = bIndexes.length;
				
				final double[] ad = a.getD();
				final double[] bd = b.getD();
				
				for (int kk=0; kk < bIndexesLength && bIndexes[kk] < end; kk += samplingStride) {

					int index = bIndexes[kk];

					if (index < start)
						continue;

					d += ad[index + offset] * bd[index];
				}

				d *= samplingStride;
				if (d > bestXcorr)
					bestXcorr = d;
			}
		}
		return bestXcorr;
	}

	private Map<String, Double> loadSampleXCorr(List<FFTPreprocessedEvent> events) throws EventException {
		
		Map<String, Double> samples = Maps.newHashMap();
		
		// all event pair keys that we want to examine
		Set<String> keys = Sets.newHashSet();
		for (Event a: events){
			for (Event b: events){
				if (!a.equals(b))
					keys.add(new EventPair(a, b).key);
			}
		}
		
		System.out.println("loading " + keys.size() + " event pairs");
		
		String line = null;
		if (!new File(XCORR_SAVE_FILE).exists()){
			try {
				new File(XCORR_SAVE_FILE).createNewFile();
			} catch (IOException e) {
				throw new EventException("failed to create cache file " + XCORR_SAVE_FILE);
			}
		}
		
		// load cached event pair xcorr values, throw away any not in our events list 
		try (BufferedReader br = new BufferedReader(new FileReader(XCORR_SAVE_FILE))){
			while (null != (line = br.readLine())) {
				String[] sa = line.split("\t");
				if (sa.length != 2)
					throw new EventException("invalid file " + XCORR_SAVE_FILE + " line: '" + line + "'");
				
				if (!keys.contains(sa[0]))
					continue;
				samples.put(sa[0], Double.parseDouble(sa[1]));
				keys.remove(sa[0]);
			}
		} catch (IOException e) {
			throw new EventException("error reading xcorr list, line '" + line + "'", e);
		}
		
		System.out.println(samples.size() + " cached pair xcorr results found, " + keys.size() + " remain to be calculated");
		
		// generate and store ones not already cached
		// store ALL pairs, not just ones above threshold - we are likely to change the threshold post-
		if (!keys.isEmpty()){
			System.out.println("calculating remaining event pair xcorr");
			
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(XCORR_SAVE_FILE, true))){
				long t0 = System.currentTimeMillis();
				int count = 0;
	
				for (int ii = 0; ii < events.size(); ii++) {
					for (int jj = ii + 1; jj < events.size(); jj++) {
	
						FFTPreprocessedEvent a = events.get(ii);
						FFTPreprocessedEvent b = events.get(jj);
						
						String key = new EventPair(a, b).key;
						if (!keys.contains(key))
							continue;
	
						double[] xcorr = Util.fftXCorr(a, b);
						double best = Util.getHighest(xcorr);
						
						bw.write(key + "\t" + Util.NF.format(best) + "\n");
						samples.put(key, best);
	
						if (++count % 1000 == 0) 
							System.out.println(count + " sample xcorr calculated ...");
					}
	
					bw.flush();
				}
				
				long tMS = System.currentTimeMillis()-t0;
				long eachMicrosec = 1000*tMS/count;
				long perSec = 1000000 / eachMicrosec;
				long allPairs = countAllEvents()*countAllEvents()/2;
				long extrapolatedForAllMS = allPairs * eachMicrosec / 1000;
				System.out.println("generated and cached " + count + " full FFT xcorr: " + tMS/1000 + " sec, " + eachMicrosec + "μs each, " + perSec + "/sec");
				System.out.println("extrapolation to full FFT xcorr for " + allPairs + " distinct pairs of " + countAllEvents() + " events: " + Util.periodToString(extrapolatedForAllMS));
				System.out.println("(delete file " + XCORR_SAVE_FILE + " to repeat)");
			} catch (IOException e) {
				throw new EventException("error writing new xcorr list", e);
			}
			
			System.out.println("finished sample xcorr calculation");
		}
		
		return samples;
	}

	private int countAllEvents() {
		return _conf.getDataset().listFiles().length;
	}
	
	private List<Event> loadAllEvents() throws EventException {
		
		System.out.println("loading events ...");
		
		List<Event> data = Lists.newArrayList();
		boolean fail=false;
		for (File f : Lists.newArrayList(_conf.getSampledataset().listFiles())){
			try{
				data.add(new Event(f, _conf));
			} catch (EventException e1){
				System.err.println("failed to load: " + e1.getMessage());
				fail=true;
			}
		}
		
		if (fail)
			throw new EventException("not all files validated");
		
		System.out.println("loaded " + data.size() + " events");

		return data;
	}

	private List<FFTPreprocessedEvent> loadSampleEvents() throws EventException {
		
		System.out.println("loading sample events ...");
		
		List<FFTPreprocessedEvent> data = Lists.newArrayList();
		boolean fail=false;
		for (File f : Lists.newArrayList(_conf.getSampledataset().listFiles())){
			try{
				data.add(new FFTPreprocessedEvent(f, _conf));
			} catch (EventException e1){
				System.err.println("failed to load: " + e1.getMessage());
				fail=true;
			}
		}
		
		if (fail)
			throw new EventException("not all files validated");
		
		
		System.out.println("loaded " + data.size() + " sample events");
		
		return data;
	}
}

package com.thaze.peakmatch;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.thaze.peakmatch.MMappedFFTCache.CreationPolicy;
import com.thaze.peakmatch.event.BasicEvent;
import com.thaze.peakmatch.event.Event;
import com.thaze.peakmatch.event.EventException;
import com.thaze.peakmatch.event.EventPair;
import com.thaze.peakmatch.event.EventPairCollector;
import com.thaze.peakmatch.event.FFTPreprocessedEvent;
import com.thaze.peakmatch.event.FFTPreprocessedEventFactory;
import com.thaze.peakmatch.event.MapCollector;

/**
 * @author Simon Rodgers
 */
public class XCorrProcessor {
	
	public static final String CONF_FILE = "xcorr.conf";
	public static final String XCORR_SAMPLE_SAVE_FILE = "xcorr.saved";
	public static final String XCORR_CANDIDATES_FILE = "xcorr.candidates";
	public static final String XCORR_POSTPROCESS_FILE = "xcorr.postprocess";
	
	private final EventProcessorConf _conf;
	private final PeakMatchProcessor pmProcessor;
	private final FFTPreprocessedEventFactory fftPreprocessedEventFactory = new FFTPreprocessedEventFactory();
	
	public XCorrProcessor() throws EventException {
		_conf = EventProcessorConf.buildFromConf(CONF_FILE).build();
		pmProcessor = new PeakMatchProcessor(_conf);
	}

	public static void main(String[] args) {
		
		long t0 = System.currentTimeMillis();
		System.out.println();
		System.out.println("*** Peak-matched sampled efficient cross-correlation ***");
		
		try{
			XCorrProcessor p = new XCorrProcessor();
			
			System.out.println("read " + CONF_FILE + " ...");
			System.out.println(p._conf);
			
			switch (p._conf.getMode()){
			case ANALYSE:
				p.analyseAccuracy();
				p.analysePerformance();
			break; case PEAKMATCH:
				p.doPeakMatch();
			break; case FFTPRECACHE:
				p.doFFTPrecache();
			break; case POSTPROCESS:
				p.doPostProcess();
			}
		} catch (EventException e){
			System.err.println("error: " + e.getMessage());
			if (null != e.getCause())
				e.printStackTrace();
		}
		
		System.out.println("*** done [" + (System.currentTimeMillis()-t0) + " ms] ***");
	}
	
	private void doFFTPrecache() throws EventException {
		
		System.out.println("loading events for precache ...");
		
		MMappedFFTCache c = new MMappedFFTCache(CreationPolicy.DELETE_OLD);

		File[] fs = _conf.getDataset().listFiles();
		
		int count = 0;
		StateLogger sl = new StateLogger();
		for (File f : fs){
			Event e = new BasicEvent(f, _conf);
			c.addToCache(new FFTPreprocessedEvent(e));
			
			if (++count % 1000 == 0)
				System.out.println(sl.state(count, fs.length));
		}
		
		c.commitIndex();
		
		System.out.println("precached " + count + " events");
	}

	private void doPostProcess() throws EventException {
		
		// load all events and self-index by name
		final List<Event> events = loadAllEvents();
		final Map<String, Event> eventsMap = Maps.uniqueIndex(events, new Function<Event, String>(){
			@Override
			public String apply(Event e) {
				return e.getName();
			}
		});
		
		// load all candidates, arrange as map of {Event : [candidate Event]} for iteration
		Multimap<Event, Event> candidates = HashMultimap.create();
		try (BufferedReader br = new BufferedReader(new FileReader(XCORR_CANDIDATES_FILE)) ){
			String line;
			while (null != (line = br.readLine())){
				
				String[] sa = line.split("\t");
				if (sa.length != 3){
					System.err.println("line invalid: '" + line + "'");
					continue;
				}
				
				Event e1 = eventsMap.get(sa[0]);
				if (null == e1)
					System.err.println("event " + sa[0] + " not found");
				Event e2 = eventsMap.get(sa[1]);
				if (null == e2)
					System.err.println("event " + sa[1] + " not found");
				
				candidates.put(e1, e2);
			}
		} catch (IOException e) {
			System.err.println("error reading file " + XCORR_CANDIDATES_FILE);
			throw new EventException(e);
		}
		
		System.out.println("loaded " + candidates.size() + " candidate pairs to test");
		
		StateLogger sl = new StateLogger();
		try (final BufferedWriter bw = new BufferedWriter(new FileWriter(XCORR_POSTPROCESS_FILE)) ){
			
			long count=0;
			for (Entry<Event, Collection<Event>> e: candidates.asMap().entrySet()){
				FFTPreprocessedEvent fe1 = fftPreprocessedEventFactory.make(e.getKey());
				
				for (Event e2: e.getValue()){
					FFTPreprocessedEvent fe2 = fftPreprocessedEventFactory.make(e2);
					
					double[] xcorr = Util.fftXCorr(fe1, fe2);
					double best = Util.getHighest(xcorr);
					
					if (best > _conf.getFinalThreshold())
						bw.write(fe1.getName() + "\t" + fe2.getName() + "\t" + best + "\n");
					
					count++;
					if (count % 1000 == 0)
						System.out.println(sl.state(count, candidates.size()));
					
					if (count % 10000 == 0)
						System.out.println(fftPreprocessedEventFactory.stats());
				}
			}
		} catch (IOException e) {
			System.err.println("error writing file " + XCORR_POSTPROCESS_FILE);
			throw new EventException(e);
		}
	}

	private void doPeakMatch() throws EventException {
		final List<Event> events = loadAllEvents();

		final long totalPairs = events.size() * events.size() / 2;
		System.out.println("starting peakmatch - " + totalPairs + " pairs");
		
		final AtomicLong count=new AtomicLong();
		final StateLogger sl = new StateLogger();
		try (final BufferedWriter bw = new BufferedWriter(new FileWriter(XCORR_CANDIDATES_FILE)) ){
			
			EventPairCollector collector = new EventPairCollector(){
				long totalPairsComplete = 0;
				int outerEventsComplete = 0;
				@Override
				public void collect(String key, double score) throws EventException{
					try {
						bw.write(key + "\t" + score + "\n");
					} catch (IOException e){
						throw new EventException(e);
					}
					count.incrementAndGet();
				}
				
				public void notifyOuterComplete(int pairsProcessed){
					totalPairsComplete += pairsProcessed;
					outerEventsComplete++;
					if (outerEventsComplete % 100 == 0)
						System.out.println(sl.state(totalPairsComplete, totalPairs));
				}
			};
			
			pmProcessor.peakmatchCandidates(events, collector, null);
			
		} catch (IOException e) {
			System.err.println("error writing file " + XCORR_CANDIDATES_FILE);
			throw new EventException(e);
		}
		
		System.out.println("Peakmatch completed - " + count.get() + " candidate pairs");
	}

	private void analyseAccuracy() throws EventException {
		List<Event> events = loadSampleEvents();
		System.out.println("found " + _conf.countAllEvents() + " full events");
		
		MapCollector candidates = new MapCollector();
		MapCollector rejections = new MapCollector();
		pmProcessor.peakmatchCandidates(events, candidates, rejections);
		
		Map<String, Double> full = loadSampleXCorr(events);
		Map<String, Double> fullAboveThreshold = reduceToFinalThreshold(full);
		
		System.out.println();
		System.out.println("*** Accuracy analysis ***");
		System.out.println(events.size() + " events sampled -> " + events.size()*events.size()/2 + " distinct pairs");
		System.out.println(fullAboveThreshold.size() + " definite XCorr event pairs above final threshold");
		System.out.println(candidates.size() + " candidates found above candidate threshold");
		
		if (fullAboveThreshold.isEmpty())
			System.out.println("no matches found in full xcorr");
		else {
		
			System.out.println();
			System.out.println("===  false positives ===");
			
			Set<String> falsePositives = Sets.newHashSet(candidates.keySet());
			falsePositives.removeAll(fullAboveThreshold.keySet());
			System.out.println(falsePositives.size() + " (" + 100*falsePositives.size()/fullAboveThreshold.size() + "% of " + fullAboveThreshold.size() + ") false positives (higher = more post-process required)");
			
			if (_conf.isVerbose()){
				for (String fp: falsePositives)
					System.out.println(fp + "\t candidate xcorr: " + Util.NF.format(candidates.get(fp)) + ", real xcorr: " + full.get(fp));
			}

			System.out.println();
			System.out.println("===  false negatives ===");
			
			Set<String> falseNegatives = Sets.newHashSet(fullAboveThreshold.keySet());
			falseNegatives.removeAll(candidates.keySet());
			
			System.out.println(falseNegatives.size() + " (" + 100*falseNegatives.size()/fullAboveThreshold.size() + "% of " + fullAboveThreshold.size() + ") false negatives (higher = more missed events)");
			if (_conf.isVerbose()){
				for (String fn: falseNegatives)
					System.out.println(fn + "\t real xcorr: " + fullAboveThreshold.get(fn) + ", candidate xcorr: " + rejections.get(fn));
			}
		}

		System.out.println();
		System.out.println("*** Accuracy analysis completed ***");
	}

	private void analysePerformance() throws EventException {
		List<Event> events = loadSampleEvents();
		
		System.out.println();
		System.out.println("*** Performance analysis ***");
		
		MapCollector candidates = new MapCollector();
		
		final int samplePairs = events.size()*events.size()/2;
		final long allPairs = _conf.countAllEvents()*_conf.countAllEvents()/2;
		
		long extrapolatedForAllMS;
		
		{
			System.out.println();
			System.out.println("=== Peakmatch phase ===");
			
			long t0 = System.currentTimeMillis();
			pmProcessor.peakmatchCandidates(events, candidates, null);
			double tPM = System.currentTimeMillis()-t0;
			
			int pairs = events.size()*events.size()/2;
			double eachMicrosec = 1000*tPM/pairs;
			double perSec = 1000000 / eachMicrosec;
			extrapolatedForAllMS = (long)(allPairs * eachMicrosec / 1000);
			
			System.out.println(events.size() + " events sampled -> " + pairs + " distinct pairs");
			System.out.println(tPM + " ms, " + (long)eachMicrosec + "μs each, " + (long)perSec + "/sec");
			
			System.out.println("Peakmatch method - extrapolation to all events (" + allPairs + " distinct pairs of " + _conf.countAllEvents() + " events): " + Util.periodToString(extrapolatedForAllMS));
		}
		
		{
			System.out.println();
			System.out.println("=== Postprocess phase ===");
		
			long t0 = System.currentTimeMillis();
			Map<String, Double> finalMatches = pmProcessor.fullFFTXCorr(candidates.keySet(), events);
			long tPostProcess = System.currentTimeMillis()-t0;
			
			long eachMicrosec = 1000*tPostProcess/candidates.size();
			long perSec = 1000000 / eachMicrosec;
			long multiple = samplePairs / candidates.size();
			long extrapolatedNaiveBFForAllMS = allPairs * eachMicrosec / 1000;
			long extrapolatedPMForAllMS = extrapolatedNaiveBFForAllMS / multiple;
			
			System.out.println(candidates.size() + " pairs post-processed with full FFT xcorr -> " + finalMatches.size() + " final matches");
			System.out.println("1 / " + multiple + " of entire eventpair space necessary to full xcorr");
			System.out.println(tPostProcess + " ms, " + eachMicrosec + "μs each, " + perSec + "/sec");
			System.out.println("extrapolation to all events (" + allPairs + " distinct pairs of " + _conf.countAllEvents() + " events): " + Util.periodToString(extrapolatedPMForAllMS));
			
			long totalPMExtrapolation = extrapolatedPMForAllMS + extrapolatedForAllMS;
			
			System.out.println();
			System.out.println("total PM + postprocess extrapolation: " + Util.periodToString(totalPMExtrapolation));
			
			System.out.println();
			System.out.println("full n^2 brute force (FFT) for comparison - extrapolation to all events: " + Util.periodToString(extrapolatedNaiveBFForAllMS));
		}
		
		System.out.println();
		System.out.println("*** Performance analysis completed ***");
	}
	
	private Map<String, Double> reduceToFinalThreshold(Map<String, Double> fullXcorr) {
		return Maps.filterValues(fullXcorr, new Predicate<Double>() {
			@Override
			public boolean apply(Double value) {
				return value >= _conf.getFinalThreshold();
			}
		});
	}
	
	public Map<String, Double> loadSampleXCorr(List<Event> events) throws EventException {
		
		Map<String, Double> samples = Maps.newHashMap();
		
		// all event pair keys that we want to examine
		Set<String> keys = Sets.newHashSet();
		for (Event a: events){
			for (Event b: events){
				if (!a.equals(b))
					keys.add(new EventPair(a, b).getKey());
			}
		}
		
		System.out.println("loading " + keys.size() + " event pairs");
		
		String line = null;
		if (!new File(XCORR_SAMPLE_SAVE_FILE).exists()){
			try {
				new File(XCORR_SAMPLE_SAVE_FILE).createNewFile();
			} catch (IOException e) {
				throw new EventException("failed to create cache file " + XCORR_SAMPLE_SAVE_FILE);
			}
		}
		
		// load cached event pair xcorr values, throw away any not in our events list 
		try (BufferedReader br = new BufferedReader(new FileReader(XCORR_SAMPLE_SAVE_FILE))){
			while (null != (line = br.readLine())) {
				String[] sa = line.split("\t");
				if (sa.length != 3)
					throw new EventException("invalid file " + XCORR_SAMPLE_SAVE_FILE + " line: '" + line + "'");
				
				String key = sa[0] + "\t" + sa[1];
				
				if (!keys.contains(key))
					continue;
				samples.put(key, Double.parseDouble(sa[2]));
				keys.remove(key);
			}
		} catch (IOException e) {
			throw new EventException("error reading xcorr list, line '" + line + "'", e);
		}
		
		System.out.println(samples.size() + " cached pair xcorr results found, " + keys.size() + " remain to be calculated");
		
		// generate and store ones not already cached
		// store ALL pairs, not just ones above threshold - we are likely to change the threshold post-
		if (!keys.isEmpty()){
			System.out.println("calculating remaining event pair xcorr");
			
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(XCORR_SAMPLE_SAVE_FILE, true))){
				long t0 = System.currentTimeMillis();
				int count = 0;
	
				for (int ii = 0; ii < events.size(); ii++) {
					for (int jj = ii + 1; jj < events.size(); jj++) {
	
						Event a = events.get(ii);
						Event b = events.get(jj);
						
						String key = new EventPair(a, b).getKey();
						if (!keys.contains(key))
							continue;
						
						double[] xcorr = Util.fftXCorr(a, b); // slowish (no precaching of FFT) but doesn't matter for samples
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
				long allPairs = _conf.countAllEvents()*_conf.countAllEvents()/2;
				long extrapolatedForAllMS = allPairs * eachMicrosec / 1000;
				System.out.println("generated and cached " + count + " full FFT xcorr: " + tMS/1000 + " sec, " + eachMicrosec + "μs each, " + perSec + "/sec");
				System.out.println("extrapolation to full FFT xcorr for " + allPairs + " distinct pairs of " + _conf.countAllEvents() + " events: " + Util.periodToString(extrapolatedForAllMS));
				System.out.println("(delete file " + XCORR_SAMPLE_SAVE_FILE + " to repeat)");
			} catch (IOException e) {
				throw new EventException("error writing new xcorr list", e);
			}
			
			System.out.println("finished sample xcorr calculation");
		}
		
		return samples;
	}
	
	private List<Event> loadAllEvents() throws EventException {
		
		System.out.println("loading events ...");
		
		List<Event> data = Lists.newArrayList();
		boolean fail=false;
		File[] fs = _conf.getDataset().listFiles();
		
		StateLogger sl = new StateLogger();
		for (File f : fs){
			try{
				Event e = new BasicEvent(f, _conf);
				data.add(e);
				
				if (data.size() % 1000 == 0){
					System.out.println(sl.state(data.size(), fs.length));
					System.gc();
				}
			} catch (EventException e1){
				System.err.println("failed to load: " + e1.getMessage());
				fail=true; // don't fail immediately, finish loading events and then bug out
			}
		}
		
		if (fail)
			throw new EventException("not all files validated");
		
		System.out.println("loaded " + data.size() + " events");

		return data;
	}

	private List<Event> loadSampleEvents() throws EventException {
		
		System.out.println("loading sample events ...");
		
		List<Event> data = Lists.newArrayList();
		boolean fail=false;
		for (File f : Lists.newArrayList(_conf.getSampledataset().listFiles())){
			try{
				data.add(new BasicEvent(f, _conf));
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

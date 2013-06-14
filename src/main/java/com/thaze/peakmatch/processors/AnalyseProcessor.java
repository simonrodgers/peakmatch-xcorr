package com.thaze.peakmatch.processors;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.thaze.peakmatch.EventProcessorConf;
import com.thaze.peakmatch.Util;
import com.thaze.peakmatch.XCorrProcessor;
import com.thaze.peakmatch.event.BasicEvent;
import com.thaze.peakmatch.event.Event;
import com.thaze.peakmatch.event.EventException;
import com.thaze.peakmatch.event.EventPair;
import com.thaze.peakmatch.event.MapCollector;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author srodgers
 *         created: 14/06/13
 */
public class AnalyseProcessor implements Processor {

	private final EventProcessorConf _conf;

	@Override
	public void process() throws EventException {
		analyseAccuracy();
		analysePerformance();
	}

	public AnalyseProcessor(EventProcessorConf conf) throws EventException {
		_conf = conf;
	}

	private void analyseAccuracy() throws EventException {
		List<Event> events = loadSampleEvents();
		System.out.println("found " + _conf.countAllEvents() + " full events");

		MapCollector candidates = new MapCollector();
		MapCollector rejections = new MapCollector();
		PeakMatchRunner.peakmatchCandidates(_conf, events, candidates, rejections);

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
			PeakMatchRunner.peakmatchCandidates(_conf, events, candidates, null);
			double tPM = System.currentTimeMillis()-t0;

			int pairs = events.size()*events.size()/2;
			double eachMicrosec = 1000*tPM/pairs;
			double perSec = 1000000 / eachMicrosec;
			extrapolatedForAllMS = (long)(allPairs * eachMicrosec / 1000);

			System.out.println(events.size() + " events sampled -> " + pairs + " distinct pairs");
			System.out.println(tPM + " ms, " + (long)eachMicrosec + " microsec each, " + (long)perSec + "/sec");

			System.out.println("Peakmatch method - extrapolation to all events (" + allPairs + " distinct pairs of " + _conf.countAllEvents() + " events): " + Util.periodToString(extrapolatedForAllMS));
		}

		{
			System.out.println();
			System.out.println("=== Postprocess phase ===");

			long t0 = System.currentTimeMillis();
			Map<String, Double> finalMatches = Util.fullFFTXCorr(_conf, candidates.keySet(), events);
			long tPostProcess = System.currentTimeMillis()-t0;

			long eachMicrosec = 1000*tPostProcess/candidates.size();
			long perSec = 1000000 / eachMicrosec;
			long multiple = samplePairs / candidates.size();
			long extrapolatedNaiveBFForAllMS = allPairs * eachMicrosec / 1000;
			long extrapolatedPMForAllMS = extrapolatedNaiveBFForAllMS / multiple;

			System.out.println(candidates.size() + " pairs post-processed with full FFT xcorr -> " + finalMatches.size() + " final matches");
			System.out.println("1 / " + multiple + " of entire eventpair space necessary to full xcorr");
			System.out.println(tPostProcess + " ms, " + eachMicrosec + " microsec each, " + perSec + "/sec");
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

	private Map<String, Double> loadSampleXCorr(List<Event> events) throws EventException {

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
		if (!new File(XCorrProcessor.XCORR_SAMPLE_SAVE_FILE).exists()){
			try {
				new File(XCorrProcessor.XCORR_SAMPLE_SAVE_FILE).createNewFile();
			} catch (IOException e) {
				throw new EventException("failed to create cache file " + XCorrProcessor.XCORR_SAMPLE_SAVE_FILE);
			}
		}

		// load cached event pair xcorr values, throw away any not in our events list
		try (BufferedReader br = new BufferedReader(new FileReader(XCorrProcessor.XCORR_SAMPLE_SAVE_FILE))){
			while (null != (line = br.readLine())) {
				String[] sa = line.split("\t");
				if (sa.length != 3)
					throw new EventException("invalid file " + XCorrProcessor.XCORR_SAMPLE_SAVE_FILE + " line: '" + line + "'");

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

			try (BufferedWriter bw = new BufferedWriter(new FileWriter(XCorrProcessor.XCORR_SAMPLE_SAVE_FILE, true))){
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
				System.out.println("generated and cached " + count + " full FFT xcorr: " + tMS/1000 + " sec, " + eachMicrosec + "microsec each, " + perSec + "/sec");
				System.out.println("extrapolation to full FFT xcorr for " + allPairs + " distinct pairs of " + _conf.countAllEvents() + " events: " + Util.periodToString(extrapolatedForAllMS));
				System.out.println("(delete file " + XCorrProcessor.XCORR_SAMPLE_SAVE_FILE + " to repeat)");
			} catch (IOException e) {
				throw new EventException("error writing new xcorr list", e);
			}

			System.out.println("finished sample xcorr calculation");
		}

		return samples;
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

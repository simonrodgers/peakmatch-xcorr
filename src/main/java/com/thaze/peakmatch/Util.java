package com.thaze.peakmatch;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.thaze.peakmatch.event.BasicEvent;
import com.thaze.peakmatch.event.Event;
import com.thaze.peakmatch.event.EventException;
import com.thaze.peakmatch.event.EventPair;
import com.thaze.peakmatch.event.FFTPreprocessedEvent;
import org.apache.commons.math.complex.Complex;
import org.apache.commons.math.stat.descriptive.SummaryStatistics;
import org.apache.commons.math.transform.FastFourierTransformer;
import org.joda.time.Period;
import org.joda.time.chrono.ISOChronology;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

import java.io.File;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Util {
	
	public final static NumberFormat NF = new DecimalFormat("#.###");
	
	private static final PeriodFormatter PF = new PeriodFormatterBuilder()
		.appendDays().appendSuffix(" day", " days")
		.appendSeparator(", ", " and ")
		.appendHours().appendSuffix(" hr")
		.appendSeparator(", ", " and ")
		.appendMinutes().appendSuffix(" min")
		.appendSeparator(" and ")
		.appendSeconds().appendSuffix(" sec")
		.toFormatter();

	public static int nextPowerOfTwo(int x) {
		return (int) Math.pow(2, Math.ceil(Math.log(x) / Math.log(2)));
	}

	public static double[] crop(double[] a, EventProcessorConf conf) throws EventException {

		if (!conf.isCrop())
			return a;

		try{

			// find peak inside 35 -> 55 sec
			// 100 points = 1 second
			double peak = -1;
			int peakIndex = 0;
	//		for (int ii = 35 * 100; ii < 55 * 100; ii++) {
			for (int ii = conf.getCropMinPeakRange(); ii < conf.getCropMaxPeakRange(); ii++) {
				if (Math.abs(a[ii]) > peak) {
					peak = Math.abs(a[ii]);
					peakIndex = ii;
				}
			}

			// return from -7 to +10 sec of peak
	//		double[] r = new double[100 * 17];

			if (conf.isVerbose()){
				System.out.println("found peak at position " + peakIndex + " (" + a[peakIndex] + ")");
				System.out.println("cropping from " + (peakIndex - conf.getCropWindowBeforePeak()) + " to " + (peakIndex + conf.getCropWindowAfterPeak()));
			}

			double[] r = new double[conf.getCropWindowBeforePeak() + conf.getCropWindowAfterPeak()];
			for (int ii = 0; ii < r.length; ii++) {
	//			r[ii] = a[ii + peakIndex - 7 * 100];
				r[ii] = a[ii + peakIndex - conf.getCropWindowBeforePeak()];
			}

			return r;
		} catch (ArrayIndexOutOfBoundsException e){
			throw new EventException("array out of bounds (" + e.getMessage() + ") while cropping - check crop parameters vs. file line count");
		}
	}
	
	// FastFourierTransformer contains some synchronized stuff which breaks multithreading if there's only one static FFT object
//	private static final FastFourierTransformer FFT = new FastFourierTransformer();
	private static final ThreadLocal<FastFourierTransformer> FFT = new ThreadLocal<FastFourierTransformer>(){
		@Override
		protected FastFourierTransformer initialValue() {
			return new FastFourierTransformer();
		}
	};
	
	public static double[] fftXCorr(Event a, Event b) {
		return fftXCorr(new FFTPreprocessedEvent(a), new FFTPreprocessedEvent(b));
	}
	
	public static double[] fftXCorr(FFTPreprocessedEvent a, FFTPreprocessedEvent b) {

		final Complex[] product = new Complex[a.getForwardFFT().length];
		for (int ii = 0; ii < a.getForwardFFT().length; ii++)
			product[ii] = a.getForwardFFT()[ii].multiply(b.getReverseFFT()[ii]);

		final Complex[] inverse = FFT.get().inversetransform(product);

		final double[] reals = new double[inverse.length];
		int ii = 0;
		for (Complex c : inverse)
			reals[ii++] = c.getReal();

		return reals;
	}
	
	public static Complex[] FFTtransform(double[] reals){
		return FFT.get().transform(reals);
	}
	
	public static double getHighest(double[] d) {
		double best = Double.MIN_VALUE;
		for (int ii = 0; ii < d.length; ii++) {
			if (d[ii] > best)
				best = d[ii];
		}

		return best; // already normalised
	}

	public static String periodToString(long ms){
		return PF.print(new Period(ms, ISOChronology.getInstanceUTC()));
	}

	public interface EventAction{ void run(Event e) throws EventException;}

	public static void executePerEvent(EventProcessorConf conf, EventAction ea) {
		File[] fs = conf.getDataset().listFiles();

		StateLogger sl = new StateLogger();
		int count = 0;
		for (File f : fs){
			try{
				Event e = new BasicEvent(f, conf);

				if (++count % 1000 == 0)
					System.out.println(sl.state(count, fs.length));

				ea.run(e);

			} catch (EventException e1){
				System.err.println("failed to load: " + e1.getMessage());
			}
		}
	}

	public static List<Event> loadAllEvents(EventProcessorConf conf) throws EventException {

		System.out.println("loading events ...");

		List<Event> data = Lists.newArrayList();
		boolean fail=false;
		File[] fs = conf.getDataset().listFiles();

		StateLogger sl = new StateLogger();
		for (File f : fs){
			try{
				Event e = new BasicEvent(f, conf);
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


	public static Map<String, Double> fullFFTXCorr(EventProcessorConf conf, final Set<String> candidateKeys, final List<Event> events) {

		final Map<String, Double> finalMatches = Maps.newHashMap();

		for (int ii = 0; ii < events.size(); ii++) {
			final Event a = events.get(ii);

			for (int jj = ii+1; jj < events.size(); jj++) {
				final Event b = events.get(jj);

				String key = new EventPair(a, b).getKey();
				if (!candidateKeys.contains(key))
					continue;

				double[] xcorr = Util.fftXCorr(new FFTPreprocessedEvent(a), new FFTPreprocessedEvent(b));
				double best = Util.getHighest(xcorr);

				if (best > conf.getFinalThreshold())
					finalMatches.put(key, best);
			}
		}

		return finalMatches;
	}

	public static double frequencyFromFFTPosition(int index, int sampleRate, int sampleCount){
		return (double)index * sampleRate / sampleCount;
	}

	public static Map<Double, Double> getBandMeans(double[] d, EventProcessorConf conf) {

		// zero pad to next power of two
		int len = Util.nextPowerOfTwo(d.length * 2);
		d = Arrays.copyOf(d, len);

		Complex[] cs = Util.FFTtransform(d);
		cs = Arrays.copyOf(cs, cs.length / 2); // second half is an inverted artifact of the transform, throw it away

		double currentBand = -1;
		SummaryStatistics currentSS = null;
		Map<Double, SummaryStatistics> bands = Maps.newLinkedHashMap();

		double filterBelowIndex = d.length / conf.getDominantFreqSampleRate() * conf.getDominantFreqFilterBelowHz();
		double filterAboveIndex = d.length / conf.getDominantFreqSampleRate() * conf.getDominantFreqFilterAboveHz();

//		for (int ii=0; ii<cs.length; ii++){
		for (int ii = (int) filterBelowIndex; ii < Math.min(cs.length, (int) filterAboveIndex); ii++) {
			double abs = cs[ii].abs();
			double freq = Util.frequencyFromFFTPosition(ii, conf.getDominantFreqSampleRate(), d.length);

			double bandStart = conf.getFrequencyBandHz() * (int)(freq / conf.getFrequencyBandHz());

			if (bandStart > currentBand) {
				currentSS = new SummaryStatistics();
				currentBand = bandStart;
				bands.put(bandStart, currentSS);
			}

			currentSS.addValue(abs);
		}

		Map<Double, Double> bandMeans = Maps.newLinkedHashMap();
		for (Map.Entry<Double, SummaryStatistics> e: bands.entrySet()){
			bandMeans.put(e.getKey(), e.getValue().getMean());
		}
		return bandMeans;
	}

	// normalise to unit vector
	public static Map<Double, Double> normaliseBandMeans(Map<Double, Double> bandMeans ){

		double sumsquares = 0d;
		for (double mean : bandMeans.values())
			sumsquares += mean * mean;
		double vectorLength = Math.sqrt(sumsquares);

		Map<Double, Double> normalisedBands = Maps.newLinkedHashMap();
		for (Map.Entry<Double, Double> e : bandMeans.entrySet())
			normalisedBands.put(e.getKey(), e.getValue() / vectorLength);

		return normalisedBands;
	}

}

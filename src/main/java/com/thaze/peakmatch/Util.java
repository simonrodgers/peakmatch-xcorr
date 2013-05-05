package com.thaze.peakmatch;

import java.text.DecimalFormat;
import java.text.NumberFormat;

import com.thaze.peakmatch.event.EventException;
import org.apache.commons.math.complex.Complex;
import org.apache.commons.math.transform.FastFourierTransformer;
import org.joda.time.Period;
import org.joda.time.chrono.ISOChronology;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

import com.thaze.peakmatch.event.Event;
import com.thaze.peakmatch.event.FFTPreprocessedEvent;

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
					peak = a[ii];
					peakIndex = ii;
				}
			}

			// return from -7 to +10 sec of peak
	//		double[] r = new double[100 * 17];

			if (conf.isVerbose())
				System.out.println("cropping from " + (peakIndex - conf.getCropWindowBeforePeak()) + " to " + (peakIndex + conf.getCropWindowAfterPeak()));

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
}

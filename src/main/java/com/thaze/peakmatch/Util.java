package com.thaze.peakmatch;

import java.text.DecimalFormat;
import java.text.NumberFormat;

import org.apache.commons.math.complex.Complex;
import org.apache.commons.math.transform.FastFourierTransformer;
import org.joda.time.Period;
import org.joda.time.chrono.ISOChronology;
import org.joda.time.format.PeriodFormat;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

public class Util {
	
	public final static NumberFormat NF = new DecimalFormat("#.###");
	
	private static final PeriodFormatter PF = new PeriodFormatterBuilder()
		.appendDays()
		.appendSuffix(" day", " days")
		.appendSeparator(", ", " and ")
		.appendHours()
		.appendSuffix(" hr")
		.appendSeparator(", ", " and ")
		.appendMinutes()
		.appendSuffix(" min")
		.appendSeparator(" and ")
		.appendSeconds()
		.appendSuffix(" sec")
		.toFormatter();

	public static String periodToString(long ms){
		return PF.print(new Period(ms, ISOChronology.getInstanceUTC()));
	}

	public static int nextPowerOfTwo(int x) {
		return (int) Math.pow(2, Math.ceil(Math.log(x) / Math.log(2)));
	}
	
	public static String memoryUsage(){
		String totalMem = Runtime.getRuntime().totalMemory() / 1024/1024 + "Mb";
		String maxMem = Runtime.getRuntime().maxMemory() / 1024/1024 + "Mb";
		return "Memory: " + totalMem + " / " + maxMem;
	}

	public static double[] crop(double[] a) {

		// TODO parameterise peak range
		// find peak inside 35 -> 55 sec
		// 100 points = 1 second
		double peak = -1;
		int peakIndex = 0;
		for (int ii = 35 * 100; ii < 55 * 100; ii++) {
			if (Math.abs(a[ii]) > peak) {
				peak = a[ii];
				peakIndex = ii;
			}
		}

		// TODO parameterise window positions
		// return from -7 to +10 sec of peak
		double[] r = new double[100 * 17];
		for (int ii = 0; ii < r.length; ii++) {
			r[ii] = a[ii + peakIndex - 7 * 100];
		}

		return r;
	}
	
	private static final FastFourierTransformer FFT = new FastFourierTransformer();
	
	public static double[] fftXCorr(Event a, Event b) {
		return fftXCorr(new FFTPreprocessedEvent(a), new FFTPreprocessedEvent(b));
	}
	
	public static double[] fftXCorr(FFTPreprocessedEvent a, FFTPreprocessedEvent b) {

		final Complex[] product = new Complex[a.getForwardFFT().length];
		for (int ii = 0; ii < a.getForwardFFT().length; ii++)
			product[ii] = a.getForwardFFT()[ii].multiply(b.getReverseFFT()[ii]);

		final Complex[] inverse = FFT.inversetransform(product);

		final double[] reals = new double[inverse.length];
		int ii = 0;
		for (Complex c : inverse)
			reals[ii++] = c.getReal();

		return reals;
	}
	
	public static Complex[] FFTtransform(double[] reals){
		return FFT.transform(reals);
	}
	
	public static double getHighest(double[] d) {
		double best = Double.MIN_VALUE;
		for (int ii = 0; ii < d.length; ii++) {
			if (d[ii] > best)
				best = d[ii];
		}

		return best; // already normalised
	}
	
	// icky static state but it's just for logging
	private static long t0 = System.currentTimeMillis();
	
	public synchronized static void initialiseStats() {
		t0 = System.currentTimeMillis();
	}

	public synchronized static String runningStats(long done, long total) {
		
		long t = System.currentTimeMillis() - t0;
		
		double each = (double)t/done;
		String eachStr = each < 1 ? NF.format(each*1000)+" μs" : NF.format(each)+" ms";
		
		String finishStr = periodToString(t * (total-done) / done);
		String takenStr = periodToString(t);
		
		return done + " / " + total + " done, taken " + takenStr + " (" + eachStr + " each), projected finish: " + finishStr + " [" + memoryUsage() + "]";  
	}
}

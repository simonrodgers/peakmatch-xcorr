package com.thaze.peakmatch;

import java.text.DecimalFormat;
import java.text.NumberFormat;

import org.apache.commons.math.complex.Complex;
import org.apache.commons.math.transform.FastFourierTransformer;
import org.joda.time.Period;
import org.joda.time.chrono.ISOChronology;
import org.joda.time.format.PeriodFormat;

public class Util {
	
	public static NumberFormat NF = new DecimalFormat("#.###");

	public static String periodToString(long ms){
		return PeriodFormat.getDefault().print(new Period(ms, ISOChronology.getInstanceUTC()));
	}

	public static int nextPowerOfTwo(int x) {
		return (int) Math.pow(2, Math.ceil(Math.log(x) / Math.log(2)));
	}

	public static double[] crop(double[] a) {

		int peakIndex = 0;

//		if (false) { // hardcoded region for debugging
//			peakIndex = 4200;
//		} else {
			// find peak inside 35 -> 55 sec
			// 100 points = 1 second
			double peak = -1;
			for (int ii = 35 * 100; ii < 55 * 100; ii++) {
				if (Math.abs(a[ii]) > peak) {
					peak = a[ii];
					peakIndex = ii;
				}
			}
//		}

		// return new int[] from -7 to +10 sec of peak
		double[] r = new double[100 * 17];
		for (int ii = 0; ii < r.length; ii++) {
			r[ii] = a[ii + peakIndex - 7 * 100];
		}

		return r;
	}
	
	private static final FastFourierTransformer fft = new FastFourierTransformer();
	
	public static double[] fftXCorr(FFTPreprocessedEvent a, FFTPreprocessedEvent b) {

		Complex[] product = new Complex[a.getForwardFFT().length];
		for (int ii = 0; ii < a.getForwardFFT().length; ii++)
			product[ii] = a.getForwardFFT()[ii].multiply(b.getReverseFFT()[ii]);

		Complex[] inverse = fft.inversetransform(product);

		double[] reals = new double[inverse.length];
		int ii = 0;
		for (Complex c : inverse)
			reals[ii++] = c.getReal();

		return reals;
	}
	
	public static Complex[] FFTtransform(double[] reals){
		return fft.transform(reals);
	}
	
	public static double getHighest(double[] d) {
		double best = Double.MIN_VALUE;
		for (int ii = 0; ii < d.length; ii++) {
			if (d[ii] > best)
				best = d[ii];
		}

		return best; // already normalised
	}
}

package com.thaze.peakmatch;

import java.io.File;
import java.util.Arrays;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math.complex.Complex;


public class FFTPreprocessedEvent extends Event {

	private final Complex[] forwards_fft;
	private final Complex[] reverse_fft;
//	private final int _peakFreq;
	
	public FFTPreprocessedEvent(File file, EventProcessorConf conf) throws EventException {
		super(file, conf);

		// precalculation of an event's forwards and reverse FFT transforms speeds up O(N^2) xcorr
		int padded_len = Util.nextPowerOfTwo(length() * 2);

		// zero pad to next power of two
		double[] forwards = Arrays.copyOf(getD(), padded_len);

		// reverse and then zero pad
		double[] reverse = Arrays.copyOf(getD(), length());
		ArrayUtils.reverse(reverse);
		reverse = Arrays.copyOf(reverse, padded_len);

		forwards_fft = Util.FFTtransform(forwards);
		reverse_fft = Util.FFTtransform(reverse);
		
//		int peakFreq=0;
//		double peakAbs=0;
//		for (int ii=0; ii<getForwardFFT().length; ii++){
//			if (getForwardFFT()[ii].abs() > peakAbs){
//				peakAbs = getForwardFFT()[ii].abs();
//				peakFreq = ii;
//			}
//		}
//		_peakFreq = peakFreq;
	}

	public Complex[] getForwardFFT() {
		return forwards_fft;
	}

	public Complex[] getReverseFFT() {
		return reverse_fft;
	}

//	public int getPeakFreq() {
//		return _peakFreq;
//	}
}

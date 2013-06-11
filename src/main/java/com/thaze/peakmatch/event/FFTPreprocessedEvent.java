package com.thaze.peakmatch.event;

import com.thaze.peakmatch.Util;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math.complex.Complex;

import java.util.Arrays;

public class FFTPreprocessedEvent implements Event {

	private final Complex[] forwards_fft;
	private final Complex[] reverse_fft;
	
	private final Event delegate;
	
	public FFTPreprocessedEvent (Event e, Complex[] forwards_fft, Complex[] reverse_fft){
		delegate = e;
		this.forwards_fft=forwards_fft;
		this.reverse_fft=reverse_fft;
	}

	public FFTPreprocessedEvent (Event e){
		delegate = e;

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
	}
	
	public Complex[] getForwardFFT() {
		return forwards_fft;
	}

	public Complex[] getReverseFFT() {
		return reverse_fft;
	}

	public int hashCode() {
		return delegate.hashCode();
	}

	public boolean equals(Object obj) {
		return delegate.equals(obj);
	}

	public double[] getD() {
		return delegate.getD();
	}

	public String getName() {
		return delegate.getName();
	}

	public int[] getMaxSpatialPeaks() {
		return delegate.getMaxSpatialPeaks();
	}

	public int[] getMinSpatialPeaks() {
		return delegate.getMinSpatialPeaks();
	}

	public int[] getIndexesAboveThreshold() {
		return delegate.getIndexesAboveThreshold();
	}

	public int length() {
		return delegate.length();
	}

	@Override
	public double getPeakAmp() {
		return delegate.getPeakAmp();
	}

	public String toString() {
		return delegate.toString();
	}
}

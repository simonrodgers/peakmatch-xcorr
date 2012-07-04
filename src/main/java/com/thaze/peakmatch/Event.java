package com.thaze.peakmatch;

public interface Event {
	public double[] getD();
	public String getName();
	public int[] getMaxSpatialPeaks();
	public int[] getMinSpatialPeaks();
	public int[] getIndexesAboveThreshold();
	public int length();
}
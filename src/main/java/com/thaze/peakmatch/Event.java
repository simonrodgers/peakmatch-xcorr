package com.thaze.peakmatch;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang.ArrayUtils;

public class Event {
	private final double[] _d;
//	private final double _rms;
	final String _filename;

//	private final int[] orderedSpatialPeaks;
	private final int[] maxSpatialPeaks;
	private final int[] minSpatialPeaks;

	// final int[] unnormalisedAmps;

	private final double _maxPeak;
	private final double _minPeak;
	private final int[] indexesAboveThreshold;

	public Event(File file, EventProcessorConf conf) throws EventException {

		String line = null;
		
		try {
			double[] d = new double[conf.getExpectedFileLineCount()];
			BufferedReader br = new BufferedReader(new FileReader(file));

			int ii = 0;
			while (null != (line = br.readLine())) {
				d[ii++] = (int) Double.parseDouble(line);
				if (ii > conf.getExpectedFileLineCount())
					throw new EventException("file " + file + " not expected size (line " + ii + " > " + conf.getExpectedFileLineCount() + ")");
			}

			if (ii != conf.getExpectedFileLineCount())
				throw new EventException("file " + file + " not expected size (" + ii + " lines != " + conf.getExpectedFileLineCount() + ")");

			br.close();
			
			_d = Util.crop(d);
		} catch (IOException e) {
			System.err.println("error reading file " + file + ", line '" + line + "'");
			throw new EventException(e);
		}

		_filename = file.getName();
		
		double ms = 0;
		for (double i : _d)
			ms += i * i;
		double rms = Math.sqrt(ms);

		// unnormalisedAmps = new int[_d.length];

		// normalise vector
		for (int ii = 0; ii < _d.length; ii++) {
			// unnormalisedAmps[ii] = (int)d[ii];
			_d[ii] /= rms;
		}

		List<Tuple<Integer, Double>> aPeaks = new ArrayList<Tuple<Integer, Double>>();
		int peakX = 0;
		double peakAmp = 0;
		for (int ii = 1; ii < length(); ii++) {
			if (_d[ii] > 0 != _d[ii - 1] > 0) { // crossing origin
				aPeaks.add(Tuple.tuple(peakX, _d[peakX]));
				peakAmp = 0;
			}

			if (Math.abs(_d[ii]) > peakAmp) {
				peakAmp = Math.abs(_d[ii]);
				peakX = ii;
			}
		}

		if (aPeaks.size() == 0) {
			System.err.println(_filename + " has no peaks :-(");
		}

		Collections.sort(aPeaks, new Comparator<Tuple<Integer, Double>>() {
			@Override
			public int compare(Tuple<Integer, Double> o1, Tuple<Integer, Double> o2) {
				return Double.compare(o2.getSecond(), o1.getSecond());
			}
		});

		_maxPeak = aPeaks.get(0).getSecond();
		_minPeak = aPeaks.get(aPeaks.size() - 1).getSecond();

//		orderedSpatialPeaks = new int[aPeaks.size()];
		maxSpatialPeaks = new int[Math.min(conf.getTopNPeaksToMatch(), aPeaks.size())];
		minSpatialPeaks = new int[Math.min(conf.getTopNPeaksToMatch(), aPeaks.size())];

		// max and min TOP_N_PEAKS peaks, ordered by abs() descending
		for (int ii = 0; ii < maxSpatialPeaks.length; ii++) {
			maxSpatialPeaks[ii] = aPeaks.get(ii).getFirst();
		}
		for (int ii = 0; ii < minSpatialPeaks.length; ii++) {
			minSpatialPeaks[ii] = aPeaks.get(aPeaks.size() - ii - 1).getFirst();
		}

//		for (int ii = 0; ii < orderedSpatialPeaks.length; ii++) {
//			orderedSpatialPeaks[ii] = aPeaks.get(ii).getFirst();
//		}

		List<Integer> t = new ArrayList<Integer>();
		for (int ii = 0; ii < _d.length; ii++) {
			if (_d[ii] > _maxPeak * conf.getTopAmplitudeThreshold() || _d[ii] < _minPeak * conf.getTopAmplitudeThreshold())
				t.add(ii);
		}
		indexesAboveThreshold = ArrayUtils.toPrimitive(t.toArray(new Integer[0]));
	}

	public int length() {
		return _d.length;
	}

	@Override
	public String toString() {
		return _filename;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((_filename == null) ? 0 : _filename.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Event other = (Event) obj;
		if (_filename == null) {
			if (other._filename != null)
				return false;
		} else if (!_filename.equals(other._filename))
			return false;
		return true;
	}

	public double[] getD() {
		return _d;
	}

//	public double getRMS() {
//		return _rms;
//	}

	public String getFilename() {
		return _filename;
	}

//	public int[] getOrderedSpatialPeaks() {
//		return orderedSpatialPeaks;
//	}

	public int[] getMaxSpatialPeaks() {
		return maxSpatialPeaks;
	}

	public int[] getMinSpatialPeaks() {
		return minSpatialPeaks;
	}

	public double getMaxPeak() {
		return _maxPeak;
	}

	public double getMinPeak() {
		return _minPeak;
	}

	public int[] getIndexesAboveThreshold() {
		return indexesAboveThreshold;
	}
}
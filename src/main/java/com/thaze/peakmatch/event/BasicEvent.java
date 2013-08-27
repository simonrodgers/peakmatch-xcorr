package com.thaze.peakmatch.event;

import com.thaze.peakmatch.EventProcessorConf;
import com.thaze.peakmatch.Tuple;
import com.thaze.peakmatch.Util;
import org.apache.commons.lang.ArrayUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class BasicEvent implements Event {
	private final double[] _d;
	private final String _filename;

	private final int[] maxSpatialPeaks;
	private final int[] minSpatialPeaks;

	private final int[] indexesAboveThreshold;

	private final double _peakAmp;

	public BasicEvent(File file, EventProcessorConf conf) throws EventException {

		// treat as relative to dataset.full if not absolute path
		if (!file.isAbsolute())
			file = new File(conf.getDataset(), file.getPath());

		String line = null;
		
		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			
			double[] d = new double[conf.getExpectedFileLineCount()];
			int ii = 0;
			while (null != (line = br.readLine())) {

				if (ii == conf.getExpectedFileLineCount()){
					if (conf.isVerbose())
						System.out.println("file " + file + " too large, truncating");

					break;
//					throw new EventException("file " + file + " not expected size (line " + ii + " > " + conf.getExpectedFileLineCount() + ")");
				}

				d[ii++] = (int) Double.parseDouble(line);
			}

			if (conf.isVerbose() && ii < conf.getExpectedFileLineCount()){
				System.out.println("file " + file + " not expected size (" + ii + " < " + conf.getExpectedFileLineCount() + "), padding to zeros");
//				throw new EventException("file " + file + " not expected size (" + ii + " lines != " + conf.getExpectedFileLineCount() + ")");
			}

			_d = Util.crop(d, conf);
		} catch (IOException e) {
			System.err.println("error reading file " + file + ", line '" + line + "'");
			throw new EventException(e);
		}

		if (conf.isNormaliseEvents()){

			double total=0;
			for (double d: _d)
				total += d;

			double offset = total / _d.length;

			for (int ii=0; ii<_d.length; ii++)
				_d[ii] -= offset;

			if (conf.isVerbose())
				System.out.println("normalised " + file + " by " + Util.NF.format(offset));
		}

		_filename = file.getName();
		
		double i2 = 0;
		for (double i : _d)
			i2 += i * i;

		// not dividing by length (RMS)
		// see http://paulbourke.net/miscellaneous/correlate/
		double rootSumOfSquares = Math.sqrt(i2);

		// normalise vector
		for (int ii = 0; ii < _d.length; ii++)
			_d[ii] /= rootSumOfSquares;

		// calculate peaks - defined as largest amplitude point between two origin-crossing
		List<Tuple<Integer, Double>> aPeaks = new ArrayList<Tuple<Integer, Double>>();
		int peakX = 0;
		double peakLocalNormalisedAmp = 0;
		double peakNormalisedAmp = 0;
		for (int ii = 1; ii < length(); ii++) {
			if (_d[ii] > 0 != _d[ii - 1] > 0) { // crossing origin
				aPeaks.add(Tuple.tuple(peakX, _d[peakX]));
				peakLocalNormalisedAmp = 0;
			}

			double d = Math.abs(_d[ii]);

			if (d > peakLocalNormalisedAmp) {
				peakLocalNormalisedAmp = d; // reset each time crossing the origin
				peakX = ii;
			}

			if (d > peakNormalisedAmp) {
				peakNormalisedAmp = d;
			}
		}

		_peakAmp = peakNormalisedAmp * rootSumOfSquares;

		if (aPeaks.size() < conf.getTopKPeaksToMatch())
			throw new EventException(getName() + " doesn't have enough peaks (" + aPeaks.size() + " found, " + conf.getTopKPeaksToMatch() + " required)");

		// sort by amplitude descending
		Collections.sort(aPeaks, new Comparator<Tuple<Integer, Double>>() {
			@Override
			public int compare(Tuple<Integer, Double> o1, Tuple<Integer, Double> o2) {
				return Double.compare(o2.getSecond(), o1.getSecond());
			}
		});

		maxSpatialPeaks = new int[conf.getTopKPeaksToMatch()];
		minSpatialPeaks = new int[conf.getTopKPeaksToMatch()];

		// TOP_N_PEAKS max (peaks) and min (troughs)
		for (int ii = 0; ii < maxSpatialPeaks.length; ii++)
			maxSpatialPeaks[ii] = aPeaks.get(ii).getFirst();
		for (int ii = 0; ii < minSpatialPeaks.length; ii++)
			minSpatialPeaks[ii] = aPeaks.get(aPeaks.size() - ii - 1).getFirst();

		double maxPeak = aPeaks.get(0).getSecond();
		double minPeak = aPeaks.get(aPeaks.size() - 1).getSecond();
		List<Integer> t = new ArrayList<Integer>();
		for (int ii = 0; ii < _d.length; ii++) {
			if (_d[ii] > maxPeak * conf.getTopAmplitudeThreshold() || _d[ii] < minPeak * conf.getTopAmplitudeThreshold())
				t.add(ii);
		}
		indexesAboveThreshold = ArrayUtils.toPrimitive(t.toArray(new Integer[0]));
	}


	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((getName() == null) ? 0 : getName().hashCode());
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
		BasicEvent other = (BasicEvent) obj;
		if (getName() == null) {
			if (other.getName() != null)
				return false;
		} else if (!getName().equals(other.getName()))
			return false;
		return true;
	}

	public double[] getD() {
		return _d;
	}

	public String getName() {
		return _filename;
	}

	public int[] getMaxSpatialPeaks() {
		return maxSpatialPeaks;
	}

	public int[] getMinSpatialPeaks() {
		return minSpatialPeaks;
	}

	public int[] getIndexesAboveThreshold() {
		return indexesAboveThreshold;
	}
	
	public int length() {
		return _d.length;
	}

	@Override
	public String toString() {
		return getName();
	}

	public double getPeakAmp() {
		return _peakAmp;
	}
}
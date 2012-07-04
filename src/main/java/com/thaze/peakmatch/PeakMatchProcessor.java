package com.thaze.peakmatch;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;


public class PeakMatchProcessor {
	
	final EventProcessorConf _conf;
	PeakMatchProcessor(EventProcessorConf conf){
		_conf=conf;
	}
	
	// peak-matching sampled brute force xcorrelation estimation
	public void peakmatchCandidates(List<Event> events, EventPairCollector successCollector, EventPairCollector rejectionCollector) throws EventException{

		for (int ii = 0; ii < events.size(); ii++) {
			final Event a = events.get(ii);

			for (int jj = ii+1; jj < events.size(); jj++) {
				final Event b = events.get(jj);
				
				double bestPositivePeakMatchXCorr = peakmatchSpecificOffset(a, b, a.getMaxSpatialPeaks(), b.getMaxSpatialPeaks());
				double bestNegativePeakMatchXCorr = peakmatchSpecificOffset(a, b, a.getMinSpatialPeaks(), b.getMinSpatialPeaks());
				
				double best = Math.max(bestPositivePeakMatchXCorr, bestNegativePeakMatchXCorr);
				
				if (best > _conf.getCandidateThreshold())
					successCollector.collect(new EventPair(a, b).key, best);
				else if (null != rejectionCollector)
					rejectionCollector.collect(new EventPair(a, b).key, best);
			}
			
			successCollector.notifyOuterComplete();
		}
	}
	

	private double peakmatchSpecificOffset(Event a, Event b, int[] aOffsets, int[] bOffsets) {
		final int len = a.getD().length;
		final int samplingStride = _conf.getSamplingStride();
		double bestXcorr = -1;

		for (int aOffset : aOffsets) {
			for (int bOffset : bOffsets) {

				final int offset = aOffset - bOffset;
				final int start = offset < 0 ? -offset : 0;
				final int end = offset < 0 ? len : len - offset;

				double d = 0;
				final int[] bIndexes = b.getIndexesAboveThreshold();
				final int bIndexesLength = bIndexes.length;
				
				final double[] ad = a.getD();
				final double[] bd = b.getD();
				
				for (int kk=0; kk < bIndexesLength && bIndexes[kk] < end; kk += samplingStride) {

					int index = bIndexes[kk];

					if (index < start)
						continue;

					d += ad[index + offset] * bd[index];
				}

				d *= samplingStride;
				if (d > bestXcorr)
					bestXcorr = d;
			}
		}
		return bestXcorr;
	}
	
	
	public Map<String, Double> fullFFTXCorr(Set<String> candidateKeys, List<Event> events) {
		
		Map<String, Double> finalMatches = Maps.newHashMap();
		
		for (int ii = 0; ii < events.size(); ii++) {
			final Event a = events.get(ii);

			for (int jj = ii+1; jj < events.size(); jj++) {
				final Event b = events.get(jj);
				
				String key = new EventPair(a, b).key; 
				if (!candidateKeys.contains(key))
					continue;
				
				double[] xcorr = Util.fftXCorr(new FFTPreprocessedEvent(a), new FFTPreprocessedEvent(b));
				double best = Util.getHighest(xcorr);
				
				if (best > _conf.getFinalThreshold())
					finalMatches.put(key, best);
			}
		}
		
		return finalMatches;
	}

}

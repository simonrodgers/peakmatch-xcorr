package com.thaze.peakmatch;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.Maps;
import com.thaze.peakmatch.event.Event;
import com.thaze.peakmatch.event.EventException;
import com.thaze.peakmatch.event.EventPair;
import com.thaze.peakmatch.event.EventPairCollector;
import com.thaze.peakmatch.event.FFTPreprocessedEvent;


public class PeakMatchProcessor {
	
	final EventProcessorConf _conf;
	PeakMatchProcessor(EventProcessorConf conf){
		_conf=conf;
	}
	
	public void peakmatchCandidates(final List<Event> events, final EventPairCollector successCollector, final EventPairCollector rejectionCollector) throws EventException{

		ExecutorService pool = Executors.newFixedThreadPool(_conf.getThreads());
		
		for (int ii = 0; ii < events.size(); ii++) {
			final Event a = events.get(ii);
			final int start = ii+1;
			
			pool.submit(new Callable<Void>() {
				@Override
				public Void call() throws EventException {

					for (int jj = start; jj < events.size(); jj++) {
						final Event b = events.get(jj);
						
						double bestPositivePeakMatchXCorr = peakmatchSpecificOffset(a, b, a.getMaxSpatialPeaks(), b.getMaxSpatialPeaks());
						double bestNegativePeakMatchXCorr = peakmatchSpecificOffset(a, b, a.getMinSpatialPeaks(), b.getMinSpatialPeaks());
						
						double best = Math.max(bestPositivePeakMatchXCorr, bestNegativePeakMatchXCorr);
						
						if (best > _conf.getCandidateThreshold())
							successCollector.collect(new EventPair(a, b).getKey(), best);
						else if (null != rejectionCollector)
							rejectionCollector.collect(new EventPair(a, b).getKey(), best);
					}
					
					successCollector.notifyOuterComplete(events.size()-start);
					
					return null;
				}
			});
		}
		
		try {
			pool.shutdown();
			pool.awaitTermination(99999, TimeUnit.DAYS);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
	}
	

	private double peakmatchSpecificOffset(Event a, Event b, int[] aOffsets, int[] bOffsets) {
		final int len = a.getD().length;
		final int samplingStride = _conf.getSamplingStride();
		double bestXcorr = -1;

		for (int aOffset : aOffsets) {
			for (int bOffset : bOffsets) {

				// tight loop - optimisation here
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
	
	
	public Map<String, Double> fullFFTXCorr(final Set<String> candidateKeys, final List<Event> events) {
		
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
				
				if (best > _conf.getFinalThreshold())
					finalMatches.put(key, best);
			}
		}
		
		return finalMatches;
	}

}

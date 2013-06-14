package com.thaze.peakmatch.processors;

import com.thaze.peakmatch.EventProcessorConf;
import com.thaze.peakmatch.event.Event;
import com.thaze.peakmatch.event.EventException;
import com.thaze.peakmatch.event.EventPair;
import com.thaze.peakmatch.event.EventPairCollector;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public class PeakMatchRunner {

	private PeakMatchRunner(){}
	
	public static void peakmatchCandidates(final EventProcessorConf conf, final List<Event> events, final EventPairCollector successCollector, final EventPairCollector rejectionCollector) throws EventException{

		ExecutorService pool = Executors.newFixedThreadPool(conf.getThreads());
		
		for (int ii = 0; ii < events.size(); ii++) {
			final Event a = events.get(ii);
			final int start = ii+1;
			
			pool.submit(new Runnable() {
				@Override
				public void run() {

					for (int jj = start; jj < events.size(); jj++) {
						final Event b = events.get(jj);
						
						double bestPositivePeakMatchXCorr = peakmatchSpecificOffset(conf.getSamplingStride(), a, b, a.getMaxSpatialPeaks(), b.getMaxSpatialPeaks());
						double bestNegativePeakMatchXCorr = peakmatchSpecificOffset(conf.getSamplingStride(), a, b, a.getMinSpatialPeaks(), b.getMinSpatialPeaks());
						
						double best = Math.max(bestPositivePeakMatchXCorr, bestNegativePeakMatchXCorr);
						
						if (best > conf.getCandidateThreshold())
							successCollector.collect(new EventPair(a, b).getKey(), best);
						else if (null != rejectionCollector)
							rejectionCollector.collect(new EventPair(a, b).getKey(), best);
					}
					
					successCollector.notifyOuterComplete(events.size()-start);
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
	

	private static double peakmatchSpecificOffset(int samplingStride, Event a, Event b, int[] aOffsets, int[] bOffsets) {
		final int len = a.getD().length;
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

}

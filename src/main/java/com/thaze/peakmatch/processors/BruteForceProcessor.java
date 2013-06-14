package com.thaze.peakmatch.processors;

import com.thaze.peakmatch.EventProcessorConf;
import com.thaze.peakmatch.StateLogger;
import com.thaze.peakmatch.Util;
import com.thaze.peakmatch.XCorrProcessor;
import com.thaze.peakmatch.event.Event;
import com.thaze.peakmatch.event.EventException;
import com.thaze.peakmatch.event.FFTPreprocessedEvent;
import com.thaze.peakmatch.event.FFTPreprocessedEventFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author srodgers
 *         created: 14/06/13
 */
public class BruteForceProcessor implements Processor {



	private final EventProcessorConf _conf;

	public BruteForceProcessor(EventProcessorConf conf) throws EventException {
		_conf = conf;
	}

	@Override
	public void process() throws EventException {
		System.out.println("brute force mode");

		final FFTPreprocessedEventFactory fftPreprocessedEventFactory = new FFTPreprocessedEventFactory(_conf.getThreads(), _conf.getFftMemoryCacheSize());

		final List<Event> events = Util.loadAllEvents(_conf);

		ExecutorService pool = Executors.newFixedThreadPool(_conf.getThreads());

		final StateLogger sl = new StateLogger();
		final AtomicInteger count = new AtomicInteger();
		final long totalSize = events.size() * events.size() / 2;

		try (final BufferedWriter bw = new BufferedWriter(new FileWriter(XCorrProcessor.XCORR_BRUTEFORCE_FILE)) ){

			for (int ii = 0; ii < events.size(); ii++) {
				final Event e1 = events.get(ii);
				final int start = ii+1;

				pool.execute(new Runnable() {
					@Override
					public void run() {

						FFTPreprocessedEvent fe1 = fftPreprocessedEventFactory.make(e1);
						for (int jj = start; jj < events.size(); jj++) {

							FFTPreprocessedEvent fe2 = fftPreprocessedEventFactory.make(events.get(jj));

							double[] xcorr = Util.fftXCorr(fe1, fe2);

							double best = Util.getHighest(xcorr);

							if (best > _conf.getFinalThreshold()){
								synchronized(bw){
									try {
										bw.write(fe1.getName() + "\t" + fe2.getName() + "\t" + best + "\n");
									} catch (IOException e) {
										System.err.println("error writing file " + XCorrProcessor.XCORR_BRUTEFORCE_FILE);
										throw new RuntimeException(e);
									}
								}
							}

							int c = count.incrementAndGet();
							if (c % 1000 == 0)
								System.out.println(sl.state(c, totalSize));

							if (c % 10000 == 0 && fftPreprocessedEventFactory.isUseCache())
								System.out.println(fftPreprocessedEventFactory.stats());
						}
					}
				});
			}

			try {
				pool.shutdown();
				pool.awaitTermination(99999, TimeUnit.DAYS);
			} catch (InterruptedException e1) {
				throw new EventException("interrupted", e1);
			}

		} catch (IOException e) {
			System.err.println("error writing file " + XCorrProcessor.XCORR_BRUTEFORCE_FILE);
			throw new EventException(e);
		}
	}
}

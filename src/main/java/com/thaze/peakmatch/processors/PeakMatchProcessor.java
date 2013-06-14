package com.thaze.peakmatch.processors;

import com.thaze.peakmatch.EventProcessorConf;
import com.thaze.peakmatch.StateLogger;
import com.thaze.peakmatch.Util;
import com.thaze.peakmatch.XCorrProcessor;
import com.thaze.peakmatch.event.Event;
import com.thaze.peakmatch.event.EventException;
import com.thaze.peakmatch.event.EventPairCollector;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author srodgers
 *         created: 14/06/13
 */
public class PeakMatchProcessor implements Processor {

	private final EventProcessorConf _conf;

	public PeakMatchProcessor(EventProcessorConf conf) throws EventException {
		_conf = conf;
	}

	@Override
	public void process() throws EventException {

		final List<Event> events = Util.loadAllEvents(_conf);

		final long totalPairs = events.size() * events.size() / 2;
		System.out.println("starting peakmatch - " + totalPairs + " pairs");

		final AtomicLong count=new AtomicLong();
		final StateLogger sl = new StateLogger();
		try (final BufferedWriter bw = new BufferedWriter(new FileWriter(XCorrProcessor.XCORR_CANDIDATES_FILE)) ){

			EventPairCollector collector = new EventPairCollector(){
				AtomicLong totalPairsComplete = new AtomicLong();
				AtomicInteger outerEventsComplete = new AtomicInteger();

				@Override
				public synchronized void collect(String key, double score) {
					try {
						bw.write(key + "\t" + score + "\n");
					} catch (IOException e){
						throw new RuntimeException(e);
					}
					count.incrementAndGet();
				}

				public void notifyOuterComplete(int pairsProcessed){
					totalPairsComplete.addAndGet(pairsProcessed);
					if (outerEventsComplete.incrementAndGet() % 100 == 0)
						System.out.println(sl.state(totalPairsComplete.get(), totalPairs));
				}
			};

			PeakMatchRunner.peakmatchCandidates(_conf, events, collector, null);

		} catch (IOException e) {
			System.err.println("error writing file " + XCorrProcessor.XCORR_CANDIDATES_FILE);
			throw new EventException(e);
		}

		System.out.println("Peakmatch completed - " + count.get() + " candidate pairs");
	}

}

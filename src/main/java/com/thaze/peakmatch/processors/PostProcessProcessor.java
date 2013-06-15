package com.thaze.peakmatch.processors;

import com.google.common.base.Function;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.thaze.peakmatch.EventProcessorConf;
import com.thaze.peakmatch.StateLogger;
import com.thaze.peakmatch.Util;
import com.thaze.peakmatch.XCorrProcessor;
import com.thaze.peakmatch.event.Event;
import com.thaze.peakmatch.event.EventException;
import com.thaze.peakmatch.event.FFTPreprocessedEvent;
import com.thaze.peakmatch.event.FFTPreprocessedEventFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author srodgers
 *         created: 14/06/13
 */
public class PostProcessProcessor implements Processor {

	private final EventProcessorConf _conf;

	public PostProcessProcessor(EventProcessorConf conf) throws EventException {
		_conf = conf;
	}

	@Override
	public void process() throws EventException {
		// load all events and self-index by name
		final List<Event> events = Util.loadAllEvents(_conf);
		final Map<String, Event> eventsMap = Maps.uniqueIndex(events, new Function<Event, String>() {
			@Override
			public String apply(Event e) {
				return e.getName();
			}
		});

		final FFTPreprocessedEventFactory fftPreprocessedEventFactory = new FFTPreprocessedEventFactory(_conf.getThreads(), _conf.getFftMemoryCacheSize());

		// load all candidates, arrange as map of {Event : [candidate Event]} for iteration
		final Multimap<Event, Event> candidates = HashMultimap.create();
		try (BufferedReader br = new BufferedReader(new FileReader(XCorrProcessor.XCORR_CANDIDATES_FILE)) ){
			String line;
			while (null != (line = br.readLine())){

				String[] sa = line.split("\t");
				if (sa.length != 3){
					System.err.println("line invalid: '" + line + "'");
					continue;
				}

				Event e1 = eventsMap.get(sa[0]);
				if (null == e1)
					System.err.println("event " + sa[0] + " not found");
				Event e2 = eventsMap.get(sa[1]);
				if (null == e2)
					System.err.println("event " + sa[1] + " not found");

				candidates.put(e1, e2);
			}
		} catch (IOException e) {
			System.err.println("error reading file " + XCorrProcessor.XCORR_CANDIDATES_FILE);
			throw new EventException(e);
		}

		System.out.println("loaded " + candidates.size() + " candidate pairs to test");

		ExecutorService pool = Executors.newFixedThreadPool(_conf.getThreads());

		final StateLogger sl = new StateLogger();
		final AtomicInteger count = new AtomicInteger();

		try (final BufferedWriter bw = new BufferedWriter(new FileWriter(XCorrProcessor.XCORR_POSTPROCESS_FILE)) ){

			for (final Map.Entry<Event, Collection<Event>> e: candidates.asMap().entrySet()){

				pool.submit(new Runnable() {
					@Override
					public void run() {
						final FFTPreprocessedEvent fe1 = fftPreprocessedEventFactory.make(e.getKey());
						for (Event e2: e.getValue()){
							FFTPreprocessedEvent fe2 = fftPreprocessedEventFactory.make(e2);

							double[] xcorr = Util.fftXCorr(fe1, fe2);

							double best = Util.getHighest(xcorr);

							if (best > _conf.getFinalThreshold()){
								synchronized(bw){
									try {
										bw.write(fe1.getName() + "\t" + fe2.getName() + "\t" + best + "\n");
									} catch (IOException ex) {
										System.err.println("error writing file " + XCorrProcessor.XCORR_POSTPROCESS_FILE);
										throw new RuntimeException(ex);
									}
								}
							}

							int c = count.incrementAndGet();
							if (c % 1000 == 0)
								System.out.println(sl.state(c, candidates.size()));

							if (c % 10000 == 0)
								System.out.println(fftPreprocessedEventFactory.stats());
						}
					}
				});
			}

			try {
				pool.shutdown();
				pool.awaitTermination(99999, TimeUnit.DAYS);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
		} catch (IOException e) {
			System.err.println("error writing file " + XCorrProcessor.XCORR_POSTPROCESS_FILE);
			throw new EventException(e);
		}
	}
}

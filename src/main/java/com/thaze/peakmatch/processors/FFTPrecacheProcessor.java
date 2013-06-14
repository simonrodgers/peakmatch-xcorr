package com.thaze.peakmatch.processors;

import com.thaze.peakmatch.EventProcessorConf;
import com.thaze.peakmatch.MMappedFFTCache;
import com.thaze.peakmatch.StateLogger;
import com.thaze.peakmatch.event.BasicEvent;
import com.thaze.peakmatch.event.Event;
import com.thaze.peakmatch.event.EventException;
import com.thaze.peakmatch.event.FFTPreprocessedEvent;

import java.io.File;

/**
 * @author srodgers
 *         created: 14/06/13
 */
public class FFTPrecacheProcessor implements Processor {

	private final EventProcessorConf _conf;

	public FFTPrecacheProcessor(EventProcessorConf conf) throws EventException {
		_conf = conf;
	}

	@Override
	public void process() throws EventException {

		System.out.println("loading events for precache ...");

		MMappedFFTCache c = new MMappedFFTCache(MMappedFFTCache.ReadWrite.WRITE);

		File[] fs = _conf.getDataset().listFiles();

		int count = 0;
		StateLogger sl = new StateLogger();
		for (File f : fs){
			Event e = new BasicEvent(f, _conf);
			c.addToCache(new FFTPreprocessedEvent(e));

			if (++count % 1000 == 0)
				System.out.println(sl.state(count, fs.length));
		}

		c.commitIndex();

		System.out.println("precached " + count + " events");
	}
}

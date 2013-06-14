package com.thaze.peakmatch;

import com.thaze.peakmatch.event.BasicEvent;
import com.thaze.peakmatch.event.Event;
import com.thaze.peakmatch.event.EventException;
import com.thaze.peakmatch.processors.AnalyseProcessor;
import com.thaze.peakmatch.processors.BruteForceProcessor;
import com.thaze.peakmatch.processors.DominantFreqProcessor;
import com.thaze.peakmatch.processors.FFTPrecacheProcessor;
import com.thaze.peakmatch.processors.PeakMatchProcessor;
import com.thaze.peakmatch.processors.PostProcessProcessor;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.File;

/**
 * @author Simon Rodgers
 */
public class XCorrProcessor {
	
	public static final String CONF_FILE = "xcorr.conf";

	public static final String XCORR_CANDIDATES_FILE = "xcorr.candidates";
	public static final String XCORR_POSTPROCESS_FILE = "xcorr.postprocess";
	public static final String XCORR_DOMINANTFREQ_FILE = "xcorr.dominantfreq";
	public static final String XCORR_BRUTEFORCE_FILE = "xcorr.bruteforce";
	public static final String XCORR_SAMPLE_SAVE_FILE = "xcorr.saved";

	public static void main(String[] args) {

		long t0 = System.currentTimeMillis();

		try{

			CommandLine cmd = null;
			try{
				Options options = new Options();
				options.addOption(OptionBuilder
						.withArgName("events")
						.hasArgs(2)
						.withDescription("filenames for events")
						.create('e'));

				CommandLineParser parser = new BasicParser();
				cmd = parser.parse(options, args);

			} catch (ParseException e) {
				System.err.println("error parsing arguments");
				e.printStackTrace();
			}

			new XCorrProcessor().run(cmd);

		} catch (Throwable e){
			System.err.println("error: " + e.getMessage());
			if (null != e.getCause())
				e.printStackTrace();
		}

		System.out.println("*** done [" + (System.currentTimeMillis()-t0) + " ms] ***");
	}

	private void run(CommandLine cmd) throws EventException {

		EventProcessorConf conf = new EventProcessorConf(CONF_FILE);

		if (cmd != null && cmd.hasOption('e')){

			String[] events = cmd.getOptionValues('e');
			if (events.length != 2){
				System.err.println("expected two event files to cross-correlate");
				return;
			}

			Event e1 = new BasicEvent(new File(events[0]), conf);
			Event e2 = new BasicEvent(new File(events[1]), conf);

			double[] xcorr = Util.fftXCorr(e1, e2);
			double best = Util.getHighest(xcorr);

			System.out.println(Util.NF.format(best));
			return;
		}

		System.out.println();
		System.out.println("*** Peakmatch ***");
		System.out.println("read " + CONF_FILE + " ...");
		System.out.println(conf);

		switch (conf.getMode()){
		case ANALYSE:
			new AnalyseProcessor(conf).process();
		break; case PEAKMATCH:
			new PeakMatchProcessor(conf).process();
		break; case FFTPRECACHE:
			new FFTPrecacheProcessor(conf).process();
		break; case POSTPROCESS:
			new PostProcessProcessor(conf).process();
		break; case BRUTEFORCE:
			new BruteForceProcessor(conf).process();
		break; case FFTDOMINANTFREQ:
			new DominantFreqProcessor(conf).process();
		}
	}
}

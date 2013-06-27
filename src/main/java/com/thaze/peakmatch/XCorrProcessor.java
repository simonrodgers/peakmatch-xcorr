package com.thaze.peakmatch;

import com.thaze.peakmatch.event.BasicEvent;
import com.thaze.peakmatch.event.Event;
import com.thaze.peakmatch.event.EventException;
import com.thaze.peakmatch.processors.AnalyseProcessor;
import com.thaze.peakmatch.processors.BruteForceProcessor;
import com.thaze.peakmatch.processors.ClusteringProcessor;
import com.thaze.peakmatch.processors.DominantFreqProcessor;
import com.thaze.peakmatch.processors.FFTPrecacheProcessor;
import com.thaze.peakmatch.processors.PeakMatchProcessor;
import com.thaze.peakmatch.processors.Plot1DProcessor;
import com.thaze.peakmatch.processors.Plot2DProcessor;
import com.thaze.peakmatch.processors.PostProcessProcessor;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

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


		try{
			EventProcessorConf conf = new EventProcessorConf(CONF_FILE);

			if (args.length > 0){
				handleCommandLine(args, conf);
				return;
			}

			new XCorrProcessor().run(conf);


		} catch (Throwable e){
			System.err.println("error: " + e.getMessage());
//			e.printStackTrace();
//			if (null != e.getCause())
//				e.printStackTrace();
		}

	}

	private static void handleCommandLine(String[] args, EventProcessorConf conf) throws EventException {

		try{
			Options options = new Options();
			options.addOption(new Option("help", "print usage"));
			options.addOption(new Option("xcorr", "cross-correlate two events"));
			options.addOption(OptionBuilder.withArgName("filenames ...").hasArgs(Option.UNLIMITED_VALUES).withDescription("event filenames - absolute or relative to dataset.full").create("events"));
			options.addOption(OptionBuilder.withArgName("dimension").hasOptionalArg().withDescription("plot events, with dimension 1d/2d/*tiny1d").create("plot"));
			options.addOption(new Option("fftdom", "perform FFTDOMINANTFREQ on events"));

			CommandLineParser parser = new BasicParser();
			CommandLine cmd = parser.parse(options, args);

			if (cmd.hasOption("events") && cmd.hasOption("plot")){
				String[] events = cmd.getOptionValues("events");

				for (String eventName: events){
					Event event = new BasicEvent(new File(eventName), conf);
					String dimension = cmd.getOptionValue("plot");
					if ("2d".equals(dimension)){
						System.out.println(Plot2DProcessor.formatEvent(event, conf));
					} else if ("1d".equals(dimension)) {
						System.out.println(Plot1DProcessor.formatEvent(event, conf, false));
					} else {
						System.out.println(Plot1DProcessor.formatEvent(event, conf, true));
					}
				}

			} else if (cmd.hasOption("events") && cmd.hasOption("xcorr")) {
				String[] events = cmd.getOptionValues("events");
				if (events.length < 2){
					System.err.println("expected two event files to cross-correlate");
					return;
				}

				Event e1 = new BasicEvent(new File(events[0]), conf);
				Event e2 = new BasicEvent(new File(events[1]), conf);

				double[] xcorr = Util.fftXCorr(e1, e2);
				double best = Util.getHighest(xcorr);

				System.out.println(Util.NF.format(best));
			} else if (cmd.hasOption("events") && cmd.hasOption("fftdom")) {

				String[] events = cmd.getOptionValues("events");

				try (Writer w = new PrintWriter(System.out)){
					for (String eventName: events){
						Event event = new BasicEvent(new File(eventName), conf);
						DominantFreqProcessor.handleEvent(conf, event, w);
						w.flush();
					}
				}

			} else {
				HelpFormatter hf = new HelpFormatter();
				hf.printHelp(100, "java -jar peakmatch.jar",null, options, null, true);
			}

		} catch (ParseException e) {
			System.err.println("error parsing arguments");
			System.err.println(e.getMessage());
		} catch (IOException e) {
			System.err.println("error writing");
			System.err.println(e.getMessage());
		}
	}

	private void run(EventProcessorConf conf) throws EventException {
		long t0 = System.currentTimeMillis();

		System.out.println();
		System.out.println("*** Peakmatch ***");
		System.out.println("read " + CONF_FILE + " ...");
//		System.out.println(conf);

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
		break; case PLOT2D:
			new Plot2DProcessor(conf).process();
		break; case PLOT1D:
			new Plot1DProcessor(conf).process();
		break; case CLUSTER:
			new ClusteringProcessor(conf).process();
		}

		System.out.println("*** done [" + (System.currentTimeMillis()-t0) + " ms] ***");
	}

//	static final double BUCKET_DURATION_SECONDS = 1.5;
//	static final double BAND_HZ = 0.5;



//	private Map<Double, Double> getBandMeans(double[] d, Complex[] cs, EventProcessorConf conf) {
//
//		double currentBand = -1;
//		SummaryStatistics currentSS = null;
//		Map<Double, SummaryStatistics> bands = Maps.newLinkedHashMap();
//
//		double filterBelowIndex = d.length / conf.getDominantFreqSampleRate() * conf.getDominantFreqFilterBelowHz();
//		double filterAboveIndex = d.length / conf.getDominantFreqSampleRate() * conf.getDominantFreqFilterAboveHz();
//
////		for (int ii=0; ii<cs.length; ii++){
//		for (int ii = (int) filterBelowIndex; ii < Math.min(cs.length, (int) filterAboveIndex); ii++) {
//			double abs = cs[ii].abs();
//			double freq = Util.frequencyFromFFTPosition(ii, conf.getDominantFreqSampleRate(), d.length);
//
//			double bandStart = BAND_HZ * (int)(freq / BAND_HZ);
//
//			if (bandStart > currentBand) {
//				currentSS = new SummaryStatistics();
//				currentBand = bandStart;
//				bands.put(bandStart, currentSS);
//			}
//
//			currentSS.addValue(abs);
//		}
//
//		Map<Double, Double> bandMeans = Maps.newLinkedHashMap();
//		for (Map.Entry<Double, SummaryStatistics> e: bands.entrySet()){
//			bandMeans.put(e.getKey(), e.getValue().getMean());
//		}
//		return bandMeans;
//	}


//	Util.executePerEvent(conf, new Util.EventAction() {
//		@Override
//		public void run(Event event) throws EventException {
//
//			double[] d = event.getD();
//
//			// zero pad to next power of two
//			int len = Util.nextPowerOfTwo(d.length * 2);
//			d = Arrays.copyOf(d, len);
//
//			Complex[] cs = Util.FFTtransform(d);
//			cs = Arrays.copyOf(cs, cs.length / 2); // second half is an inverted artifact of the transform, throw it away
//
//			Map<Double, Double> bandMeans = getBandMeans(d, cs, conf);
//
//			System.out.println(event.getName());
//			for (Map.Entry<Double, Double> e: bandMeans.entrySet()){
//				Double bandStart = e.getKey();
//				Double mean = e.getValue();
//
//				System.out.print(StringUtils.rightPad("" + bandStart, 8));
//				for (double dd=0; dd<mean; dd+=0.05d){
//					System.out.print("#");
//				}
//				System.out.println();
//			}
//		}
//	});
}

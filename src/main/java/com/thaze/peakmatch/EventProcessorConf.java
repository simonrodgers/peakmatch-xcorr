package com.thaze.peakmatch;

import com.thaze.peakmatch.event.EventException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

public class EventProcessorConf {

	private final File dataset;
	private final File sampledataset;
	private final int topKPeaksToMatch;
	private final int samplingStride;
	private final double topAmplitudeThreshold;
	private final double candidateThreshold;
	private final double finalThreshold;
	private final int expectedFileLineCount;
	private final Mode mode;
	private final boolean verbose;
	private final int threads;
	private final int fftMemoryCacheSize;

	private final boolean crop;

	private final int cropMinPeakRange;
	private final int cropMaxPeakRange;
	private final int cropWindowBeforePeak;
	private final int cropWindowAfterPeak;

	private final int dominantFreqSampleRate;
	private final double dominantFreqBandWidth;
	private final double dominantFreqFilterBelowHz;
	private final double dominantFreqFilterAboveHz;
	private final int dominantFreqTopFreqCount;

	private final boolean normaliseEvents;
	private final String meanFrequencyAmplitudeBands;


	private final double frequencyBandHz;
	private final double plot2dBucketDurationSec;
	private final PlotGradient plot2dGradient;
	private final boolean plot1dTiny;

	private final int clusterK;
	private final double clusterEta;
	private final String clusterCentres;
	private final double clusterCentreThreshold;

	public EventProcessorConf(String confFile) throws EventException {

		Properties props = getProps(confFile);

		dataset = new File(props.getProperty("dataset.full"));
		if (!dataset.exists() || !dataset.isDirectory() || dataset.listFiles().length == 0)
			throw new EventException("non-existent or empty dataset directory " + dataset);

		sampledataset = new File(props.getProperty("dataset.sample"));
//		if (!sampledataset.exists() || !sampledataset.isDirectory() || sampledataset.listFiles().length == 0)
//			throw new EventException("non-existent or empty sample dataset directory " + sampledataset);

		topKPeaksToMatch = getInt(props, "top-k-peaks");
		samplingStride = getInt(props, "sampling-stride");
		topAmplitudeThreshold = getDouble(props, "top-amplitude-threshold");
		candidateThreshold = getDouble(props, "candidate-threshold");
		finalThreshold = getDouble(props, "final-threshold");
		expectedFileLineCount = getInt(props, "expected-file-line-count");

		try {
			mode = Mode.valueOf(props.getProperty("mode"));
		} catch (IllegalArgumentException e) {
			throw new EventException("invalid mode value '" + props.getProperty("mode") + "'");
		}
		verbose = Boolean.parseBoolean(props.getProperty("verbose"));
		threads = getInt(props, "threads");
		fftMemoryCacheSize = getInt(props, "fft-memory-cache-size");

		crop = Boolean.parseBoolean(props.getProperty("crop"));
		cropMinPeakRange = getInt(props, "crop.min-peak-range");
		cropMaxPeakRange = getInt(props, "crop.max-peak-range");
		cropWindowBeforePeak = getInt(props, "crop.window-before-peak");
		cropWindowAfterPeak = getInt(props, "crop.window-after-peak");

		dominantFreqBandWidth = getDouble(props, "dominantfreq.band-width");
		dominantFreqFilterBelowHz = getDouble(props, "dominantfreq.filter-below-hz");
		dominantFreqFilterAboveHz = getDouble(props, "dominantfreq.filter-above-hz");
		dominantFreqSampleRate = getInt(props, "dominantfreq.sample-rate");
		dominantFreqTopFreqCount = getInt(props, "dominantfreq.top-freq-count");

		normaliseEvents = Boolean.parseBoolean(props.getProperty("normalise-events"));

		meanFrequencyAmplitudeBands = props.getProperty("dominantfreq.mean-frequency-amplitude-bands");

		frequencyBandHz = getDouble(props, "frequency.band-hz");
		plot2dBucketDurationSec = getDouble(props, "plot.2d.bucket-duration-sec");

		try {
			plot2dGradient = PlotGradient.valueOf(props.getProperty("plot.2d.gradient"));
		} catch (IllegalArgumentException e) {
			throw new EventException("invalid gradient value '" + props.getProperty("plot2d.gradient") + "'");
		}
		plot1dTiny = Boolean.parseBoolean(props.getProperty("plot.1d.tiny"));

		clusterK = getInt(props, "cluster.k");
		clusterEta = getDouble(props, "cluster.eta");
		clusterCentres = props.getProperty("cluster.centres", ""); // can be unset = no centres
		clusterCentreThreshold = getDouble(props, "cluster.centre-threshold", 0);

	}

	static double getDouble(Properties props, String key) throws EventException {
		String s = props.getProperty(key);
		if (null == s)
			throw new EventException("missing conf value: " + key);
		try {
			return Double.parseDouble(s);
		} catch (NumberFormatException e) {
			throw new EventException("invalid double value for key " + key + ": " + s);
		}
	}

	static double getDouble(Properties props, String key, double def) throws EventException {
		String s = props.getProperty(key);
		if (null == s)
			return def;
		try {
			return Double.parseDouble(s);
		} catch (NumberFormatException e) {
			return def;
		}
	}


	static int getInt(Properties props, String key) throws EventException {
		String s = props.getProperty(key);
		if (null == s)
			throw new EventException("missing conf value: " + key);
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			throw new EventException("invalid int value for key " + key + ": " + s);
		}
	}

	public boolean isNormaliseEvents() {
		return normaliseEvents;
	}

	public static enum Mode {
		ANALYSE, PEAKMATCH, FFTPRECACHE, POSTPROCESS, BRUTEFORCE, FFTDOMINANTFREQ, PLOT2D, PLOT1D, CLUSTER
	}

	public static enum PlotGradient {
		// █ ▆ ▃ etc
		VERTICAL	(new char[]{' ', 0x2581, 0x2582, 0x2583, 0x2584, 0x2585, 0x2586, 0x2587, 0x2588}),
		// █ ▉ ▌ ▎etc
		HORIZONTAL	(new char[]{' ', 0x258F, 0x258E, 0x258D, 0x258C, 0x258B, 0x258A, 0x2589, 0x2588}),
		// █ ▓ ░
		SHADED		(new char[]{' ', 0x2591, 0x2592, 0x2593, 0x2588});

		char[] _chars;

		public char[] getChars(){
			return _chars;
		}

		PlotGradient(char[] chars){
			_chars=chars;
		}
	}

	public File getDataset() {
		return dataset;
	}

	public File getSampledataset() {
		return sampledataset;
	}

	public int getTopKPeaksToMatch() {
		return topKPeaksToMatch;
	}

	public int getSamplingStride() {
		return samplingStride;
	}

	public double getTopAmplitudeThreshold() {
		return topAmplitudeThreshold;
	}

	public double getCandidateThreshold() {
		return candidateThreshold;
	}

	public double getFinalThreshold() {
		return finalThreshold;
	}

	public int getExpectedFileLineCount() {
		return expectedFileLineCount;
	}

	public Mode getMode() {
		return mode;
	}

	public boolean isVerbose() {
		return verbose;
	}

	public int getThreads() {
		return threads;
	}

	public int getFftMemoryCacheSize() {
		return fftMemoryCacheSize;
	}

	public boolean isCrop() {
		return crop;
	}

	public int getCropMinPeakRange() {
		return cropMinPeakRange;
	}

	public int getCropMaxPeakRange() {
		return cropMaxPeakRange;
	}

	public int getCropWindowBeforePeak() {
		return cropWindowBeforePeak;
	}

	public int getCropWindowAfterPeak() {
		return cropWindowAfterPeak;
	}

	public int getDominantFreqSampleRate() {
		return dominantFreqSampleRate;
	}

	public double getDominantFreqBandWidth() {
		return dominantFreqBandWidth;
	}

	public double getDominantFreqFilterBelowHz() {
		return dominantFreqFilterBelowHz;
	}

	public double getDominantFreqFilterAboveHz() {
		return dominantFreqFilterAboveHz;
	}

	public int getDominantFreqTopFreqCount() {
		return dominantFreqTopFreqCount;
	}


	public String getMeanFrequencyAmplitudeBands() {
		return meanFrequencyAmplitudeBands;
	}

	public double getFrequencyBandHz() {
		return frequencyBandHz;
	}
	public double getPlot2dBucketDurationSec() {
		return plot2dBucketDurationSec;
	}
	public PlotGradient getPlot2dGradient() {
		return plot2dGradient;
	}
	public boolean isPlot1dTiny() {
		return plot1dTiny;
	}

	public int getClusterK() {
		return clusterK;
	}
	public double getClusterEta() {
		return clusterEta;
	}
	public String getClusterCentres() {
		return clusterCentres;
	}

	public double getClusterCentreThreshold() {
		return clusterCentreThreshold;
	}




//	@Override
//	public String toString() {
//		return 	"dataset=" + dataset +
//				"\nsampledataset=" + sampledataset +
//				"\ntopKPeaksToMatch=" + topKPeaksToMatch +
//				"\nsamplingStride=" + samplingStride +
//				"\ntopAmplitudeThreshold=" + topAmplitudeThreshold +
//				"\ncandidateThreshold=" + candidateThreshold +
//				"\nfinalThreshold=" + finalThreshold +
//				"\nexpectedFileLineCount=" + expectedFileLineCount +
//				"\nmode=" + mode +
//				"\nverbose=" + verbose +
//				"\nthreads=" + threads +
//				"\nfftMemoryCacheSize=" + fftMemoryCacheSize +
//				"\ncrop=" + crop +
//				"\ncropMinPeakRange=" + cropMinPeakRange +
//				"\ncropMaxPeakRange=" + cropMaxPeakRange +
//				"\ncropWindowBeforePeak=" + cropWindowBeforePeak +
//				"\ncropWindowAfterPeak=" + cropWindowAfterPeak +
//				"\ndominantFreqSampleRate=" + dominantFreqSampleRate +
//				"\ndominantFreqBandWidth=" + dominantFreqBandWidth +
//				"\ndominantFreqFilterBelowHz=" + dominantFreqFilterBelowHz +
//				"\ndominantFreqFilterAboveHz=" + dominantFreqFilterAboveHz +
//				"\ndominantFreqTopFreqCount=" + dominantFreqTopFreqCount;
//	}

	private static Properties getProps(String filename) throws EventException {

		File propsFile = new File(filename);
		if (!propsFile.exists())
			throw new EventException("missing conf file " + filename);

		Properties props = new Properties();
		try {
			props.load(new FileReader(propsFile));
		} catch (IOException e) {
			throw new EventException(e);
		}
		return props;
	}

	public int countAllEvents() {
		return getDataset().listFiles().length;
	}
}

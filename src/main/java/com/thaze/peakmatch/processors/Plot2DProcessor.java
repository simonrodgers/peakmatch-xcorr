package com.thaze.peakmatch.processors;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.thaze.peakmatch.EventProcessorConf;
import com.thaze.peakmatch.Util;
import com.thaze.peakmatch.event.Event;
import com.thaze.peakmatch.event.EventException;
import org.apache.commons.lang.StringUtils;

import java.util.Arrays;
import java.util.Map;

/**
 * @author srodgers
 *         created: 14/06/13
 */
public class Plot2DProcessor implements Processor {

	private final EventProcessorConf _conf;

	public Plot2DProcessor(EventProcessorConf conf) throws EventException {
		_conf = conf;
	}

	@Override
	public void process() throws EventException {

		System.out.println("2d-plotting all events in dataset.full");

		Util.executePerEvent(_conf, new Util.EventAction() {
			@Override
			public void run(Event event) throws EventException {
				System.out.println(formatEvent(event, _conf));
			}
		});
	}

	public static String formatEvent(Event event, EventProcessorConf conf){
		StringBuffer out = new StringBuffer();

		final char[] gradient = conf.getPlot2dGradient().getChars();

		int l = event.getD().length;
		int bucketSize = (int) (conf.getPlot2dBucketDurationSec() * conf.getDominantFreqSampleRate());

		Map<Double,StringBuffer> lines = Maps.newLinkedHashMap();

		out.append(event.getName() + "\n");

		for (int ii = 0; ii < l; ii += bucketSize) {
			Map<Double, Double> bandMeans = Util.getBandMeans(Arrays.copyOfRange(event.getD(), ii, ii + bucketSize), conf);

			bandMeans = Util.normaliseBandMeans(bandMeans);

			for (Map.Entry<Double, Double> e : bandMeans.entrySet()) {
				int index = Math.min(gradient.length - 1, (int) (gradient.length * e.getValue() / 0.3d));
				double freq = e.getKey();

				StringBuffer sb = lines.get(freq);

				if (sb == null) {
					sb = new StringBuffer();
					lines.put(freq, sb);
				}

				sb.append(gradient[index]);
			}
		}

		int linelength = 0;
		for (Map.Entry<Double, StringBuffer> line : Lists.reverse(Lists.newArrayList(lines.entrySet()))) {
			out.append(StringUtils.leftPad(Util.NF.format(line.getKey()) + " hz ", 10));
			out.append(line.getValue().toString());
			out.append("\n");
			linelength = line.getValue().length();
		}

		out.append(StringUtils.leftPad("sec ", 10));
		int spacing = 10;

		for (int ii = 0; ii < linelength; ii += spacing) {
			out.append(StringUtils.rightPad("|" + Util.NF.format(conf.getPlot2dBucketDurationSec() * ii), spacing));
		}
		out.append("\n\n");

		return out.toString();
	}

}

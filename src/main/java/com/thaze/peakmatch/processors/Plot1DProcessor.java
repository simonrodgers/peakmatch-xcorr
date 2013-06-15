package com.thaze.peakmatch.processors;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.thaze.peakmatch.EventProcessorConf;
import com.thaze.peakmatch.Util;
import com.thaze.peakmatch.event.Event;
import com.thaze.peakmatch.event.EventException;
import org.apache.commons.lang.StringUtils;

import java.util.Map;

/**
 * @author srodgers
 *         created: 14/06/13
 */
public class Plot1DProcessor implements Processor {

	private final EventProcessorConf _conf;

	public Plot1DProcessor(EventProcessorConf conf) throws EventException {
		_conf = conf;
	}

	static final int ROWS = 20;
	static final int spacing = 10;

	@Override
	public void process() throws EventException {
		System.out.println("1d-plotting all events in dataset.full");
		if (_conf.isPlot1dTiny())
			System.out.println("tiny mode");

		Util.executePerEvent(_conf, new Util.EventAction() {
			@Override
			public void run(Event event) throws EventException {
				System.out.println(formatEvent(event, _conf, _conf.isPlot1dTiny()));
			}
		});
	}

	public static String formatEvent(Event event, EventProcessorConf conf, boolean plotTiny){
		Map<Double, Double> bandMeans = Util.getBandMeans(event.getD(), conf);
		return formatEvent(event, bandMeans, conf, plotTiny);
	}

	public static String formatEvent(Event event, Map<Double, Double> bandMeans, EventProcessorConf conf, boolean plotTiny) {
		Map<Double,StringBuffer> lines = Maps.newLinkedHashMap();

		double max = 0;
		for (Double mean: bandMeans.values()){
			if (mean > max)
				max = mean;
		}

		StringBuffer out = new StringBuffer();

		if (plotTiny){
			out.append(event.getName() + "\t");
			for (Map.Entry<Double, Double> e: bandMeans.entrySet()){
				Double mean = e.getValue();
				int index = (int)((EventProcessorConf.PlotGradient.VERTICAL.getChars().length-1) * mean / max);
				out.append(EventProcessorConf.PlotGradient.VERTICAL.getChars()[index]);
			}

			out.append("#");

		} else {

			out.append(event.getName() + "\n");
			for (Map.Entry<Double, Double> e: bandMeans.entrySet()){
				Double mean = e.getValue();

				for (double dd=0; dd<max; dd+=max/ROWS){

					StringBuffer sb = lines.get(dd);

					if (sb == null) {
						sb = new StringBuffer();
						lines.put(dd, sb);
					}

					if (dd <= mean)
						sb.append((char)0x258A); // █
					else
						sb.append(' ');
				}
			}

			int linelength = 0;
			for (Map.Entry<Double, StringBuffer> line : Lists.reverse(Lists.newArrayList(lines.entrySet()))) {
				out.append(StringUtils.leftPad(Util.NF.format(line.getKey()) + " ", 10));
				out.append(line.getValue().toString() + "\n");
				linelength = line.getValue().length();
			}

			out.append(StringUtils.leftPad("hz ", 10));

			for (int ii = 0; ii < linelength; ii += spacing) {
				out.append(StringUtils.rightPad("|" + Util.NF.format(conf.getFrequencyBandHz() * ii + conf.getDominantFreqFilterBelowHz()), spacing));
			}
			out.append("\n");
		}

		return out.toString();
	}
}

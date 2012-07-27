package com.thaze.peakmatch;

import org.joda.time.Period;
import org.joda.time.chrono.ISOChronology;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

public class StateLogger {
	
	private final long t0;
	
	public StateLogger(){
		t0 = System.currentTimeMillis();
	}
	
	public synchronized String state(long done, long total) {
		
		long t = System.currentTimeMillis() - t0;
		
		double each = (double)t/done;
		String eachStr = each < 1 ? Util.NF.format(each*1000)+" μs" : Util.NF.format(each)+" ms";
		
		String finishStr = Util.periodToString(t * (total-done) / done);
		String takenStr = Util.periodToString(t);
		
		return done + " / " + total + " done, taken " + takenStr + " (" + eachStr + " each), projected finish: " + finishStr + " [" + memoryUsage() + "]";  
	}
	
	public static String memoryUsage(){
		String totalMem = Runtime.getRuntime().totalMemory() / 1024/1024 + "Mb";
		String maxMem = Runtime.getRuntime().maxMemory() / 1024/1024 + "Mb";
		return "Memory: " + totalMem + " / " + maxMem;
	}



}

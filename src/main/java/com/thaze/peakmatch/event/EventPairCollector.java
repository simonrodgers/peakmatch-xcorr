package com.thaze.peakmatch.event;

public interface EventPairCollector{
	void collect(String key, double score);
	void notifyOuterComplete(int pairsProcessed);
}
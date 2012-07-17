package com.thaze.peakmatch.event;

public interface EventPairCollector{
	void collect(String key, double score) throws EventException;
	void notifyOuterComplete(int pairsProcessed);
}
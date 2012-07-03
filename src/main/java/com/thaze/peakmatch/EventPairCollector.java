package com.thaze.peakmatch;

interface EventPairCollector{
	void collect(String key, double score) throws EventException;
	void notifyOuterComplete();
}
package com.thaze.peakmatch.event;

import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

public class MapCollector implements EventPairCollector {
	
	private final Map<String, Double> data = Maps.newHashMap();
	
	public MapCollector(){
	}

	@Override
	public void collect(String key, double score) {
		data.put(key, score);
	}
	public int size(){
		return data.size();
	}
	public Double get(String key){
		return data.get(key);
	}
	public Set<String> keySet(){
		return ImmutableSet.copyOf(data.keySet());
	}
	public void notifyOuterComplete(int pairsProcessed){
		// no-op
	}
}
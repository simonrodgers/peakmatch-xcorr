package com.thaze.peakmatch;

import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

class MapCollector implements EventPairCollector {
	private final Map<String, Double> data = Maps.newHashMap();
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
	public void notifyOuterComplete(){
		// no-op
	}
}
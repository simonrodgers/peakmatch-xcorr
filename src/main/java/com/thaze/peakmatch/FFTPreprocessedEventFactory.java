package com.thaze.peakmatch;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;

public class FFTPreprocessedEventFactory {

	LoadingCache<Event, FFTPreprocessedEvent> cache = CacheBuilder
			.newBuilder()
			.maximumSize(2000)
			.build(new CacheLoader<Event, FFTPreprocessedEvent>() {
				public FFTPreprocessedEvent load(Event e) {
					return new FFTPreprocessedEvent(e);
				}
			});

	public FFTPreprocessedEventFactory() {}
	
	public FFTPreprocessedEvent make(Event e) {
		return cache.getUnchecked(e);
	}
	
//	public long getCacheSize(){
//		return cache.size();
//	}
	
	public CacheStats stats(){
		return cache.stats();
	}
}
	
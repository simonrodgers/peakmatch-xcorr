package com.thaze.peakmatch.event;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.thaze.peakmatch.MMappedFFTCache;
import com.thaze.peakmatch.MMappedFFTCache.ReadWrite;

public class FFTPreprocessedEventFactory {
	
	private final MMappedFFTCache fftcache = new MMappedFFTCache(ReadWrite.READ);
	
	// smallish JVM cache ontop of memory mapped file
	private final LoadingCache<Event, FFTPreprocessedEvent> cache;
	
	public FFTPreprocessedEventFactory(int threads){
		cache = CacheBuilder.newBuilder()
			.concurrencyLevel(threads)
			.maximumSize(2000)
			.build(new CacheLoader<Event, FFTPreprocessedEvent>() {
				public FFTPreprocessedEvent load(Event e) {
					return fftcache.read(e);
//							return new FFTPreprocessedEvent(e);
				}
			});
	}

	public FFTPreprocessedEvent make(Event e) {
		return cache.getUnchecked(e);
	}
	
	public CacheStats stats(){
		return cache.stats();
	}
}
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
	
	private final boolean useCache;
	
	public FFTPreprocessedEventFactory(int threads, int cacheSize){
		cache = CacheBuilder.newBuilder()
			.concurrencyLevel(threads)
			.maximumSize(cacheSize)
			.build(new CacheLoader<Event, FFTPreprocessedEvent>() {
				public FFTPreprocessedEvent load(Event e) {
					return fftcache.read(e);
				}
			});
		
		useCache = cacheSize > 0;
	}

	public FFTPreprocessedEvent make(Event e) {
		if (isUseCache())
			return cache.getUnchecked(e);
		return fftcache.read(e);
	}
	
	public CacheStats stats(){
		return cache.stats();
	}

	public boolean isUseCache() {
		return useCache;
	}
}
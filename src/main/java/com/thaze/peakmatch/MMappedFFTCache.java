package com.thaze.peakmatch;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.sf.json.JSONObject;

import org.apache.commons.io.FileUtils;
import org.apache.commons.math.complex.Complex;

import vanilla.java.chronicle.Excerpt;
import vanilla.java.chronicle.impl.IndexedChronicle;

import com.thaze.peakmatch.event.Event;
import com.thaze.peakmatch.event.FFTPreprocessedEvent;

/**
 * Memory mapped fast lookup cache of event FFTs (forwards and reverse)
 * This is only limited by disk size, doesn't store anything on the heap
 * 
 * Leave a good chunk of system memory free for this; heavily relies on the pagecache
 * Should be run on a 64-bit OS or it'll run out of addressable space quite fast (more than ~2k events)
 * 
 * uses Chronicle library to partition (to around Java's 2GB MappedByteBuffer limit) and index entries (numeric index)
 * also stores a separate JSON file to index {event name : chronicle numeric id}
 * 
 * see https://github.com/peter-lawrey/Java-Chronicle 
 * 
 * @author Simon Rodgers
 */
public class MMappedFFTCache {
	
	public final static File INDEXFILE = new File("fftcache.index.json");
	public final static String CHRONICLEFILE = "fftcache.chronicle";
	
	private final IndexedChronicle chronicle;
	private final JSONObject index;
	
	private final Map<String, Integer> indexReadOnlyMap; // for concurrent access
	
	public enum ReadWrite {READ, WRITE}
	
	private final ReadWrite rw;

	@SuppressWarnings("unchecked")
	public MMappedFFTCache(ReadWrite rw) {
		
		this.rw=rw;
		
		if (rw == ReadWrite.WRITE){
			INDEXFILE.delete();
			new File(CHRONICLEFILE + ".data").delete();
			new File(CHRONICLEFILE + ".index").delete();
		}
		
		try {
			chronicle = new IndexedChronicle(CHRONICLEFILE, 24); // magic number 24 for dataBitHintSize - it's what is used in the various examples
			
			if (!INDEXFILE.exists())
				FileUtils.writeStringToFile(INDEXFILE, "{}");
			
			index = JSONObject.fromObject(FileUtils.readFileToString(INDEXFILE));
		} catch (IOException e){
			throw new RuntimeException("failed to read/write to chronicle or index file", e);
		}
		
		indexReadOnlyMap = Collections.unmodifiableMap(new ConcurrentHashMap<String, Integer>(index));
	}

	public FFTPreprocessedEvent read(Event event) {
		
		Long key = getIndex(event.getName());
		if (key == null)
			throw new IllegalStateException("event " + event.getName() + " FFT not precached - make sure FFTPRECACHE mode run");
		
		Excerpt<IndexedChronicle> e = chronicle.createExcerpt();
		if (!e.index(key))
			throw new IllegalStateException("event " + event.getName() + " in JSON index but key " + key + " not in chronicle");
		
		Complex[] forwards_fft = new Complex[e.readInt()];
		for (int ii=0; ii<forwards_fft.length; ii++)
			forwards_fft[ii] = new Complex(e.readDouble(), e.readDouble());
		
		Complex[] reverse_fft = new Complex[e.readInt()];
		for (int ii=0; ii<reverse_fft.length; ii++)
			reverse_fft[ii] = new Complex(e.readDouble(), e.readDouble());
		
		e.finish();
		
		return new FFTPreprocessedEvent(event, forwards_fft, reverse_fft);
	}
	
	/**
	 * write index to disk
	 * @throws IOException
	 */
	public void commitIndex() {
		try {
			FileUtils.writeStringToFile(INDEXFILE, index.toString(2));
		} catch (IOException e){
			throw new RuntimeException("failed to write to index file", e);
		}
	}
	
	private void putIndex(String name, long key) {
		index.put(name, key);
		if (index.size() % 1000 == 0)
			commitIndex();
	}
	
	private Long getIndex(String name) {
		
//		return index.optLong(name, -1);
		Integer i = indexReadOnlyMap.get(name);
		if (null == i)
			return null;
		return new Long(i.longValue());
	}

	public void addToCache(FFTPreprocessedEvent event) {
		
		if (rw != ReadWrite.WRITE)
			throw new IllegalStateException("attempting to write to cache not in WRITE mode");
		
		Excerpt<IndexedChronicle> e = chronicle.createExcerpt();
		
		// capacity: 8 bytes per double, 2 doubles per Complex
		e.startExcerpt((event.getForwardFFT().length*2 + event.getReverseFFT().length*2 + 2) * 8);
		
		e.writeInt(event.getForwardFFT().length);
		for (Complex c : event.getForwardFFT()){
			e.writeDouble(c.getReal());
			e.writeDouble(c.getImaginary());
		}
		
		e.writeInt(event.getReverseFFT().length);
		for (Complex c : event.getReverseFFT()){
			e.writeDouble(c.getReal());
			e.writeDouble(c.getImaginary());
		}
		e.finish();
		
		putIndex(event.getName(), chronicle.size()-1);
	}
}

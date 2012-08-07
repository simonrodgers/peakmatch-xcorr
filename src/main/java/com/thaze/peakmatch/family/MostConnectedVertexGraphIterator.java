package com.thaze.peakmatch.family;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.google.common.base.Function;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Collections2;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.thaze.peakmatch.family.MostConnectedVertexGraphIterator.Vertex;

/**
 * Specialised graph implementation with specific functionality to efficiently remove most-connected vertices (highest degree).<br/>
 * <br/>
 * Extraction of the most connected vertex reduces the degree of its immediate siblings, and potentially changes the ordering of 
 * subsequent removal.<br/> 
 * <br/>
 * This re-ordering is handled efficiently; the implementation is similar to pigeonhole sort - a 
 * non-comparison-based sort, which isn't subject to the lower bounds on comparison sorts' running time of O(n log n)<br/>
 *<br/>
 * Algorithm:<br/>
 * <br/>
 * <blockquote><pre>
 * Build an ordered map M of {integer degree : [set of all vertices with that degree]}
 * While M is not empty:
 *   an arbitrary vertex V is removed from the set S with highest degree
 *   for each of V's immediately connected vertices O :
 *     V is removed from O's connections
 *     O's degree is lowered by 1, and moved to the correct set within M
 * </pre></blockquote>
 * running time scales as O((m + n) * log (D)) to iterate over all vertices, where m = #edges, n = #vertices, D = cardinality of degrees of all vertices<br/>
 * <br/>
 * usage:<br/>
 * <br/>
 * <blockquote><pre>
 * Builder b = MostConnectedVertexGraphIterator.newBuilder();
 * b.addEdge("a", "b");
 * b.addEdge("a", "c");
 * b.addEdge("c", "d");
 * ...
 * MostConnectedVertexGraphIterator g = b.build();
 * while (g.hasNext()){
 *		Vertex v = g.next();
 *		for (Vertex o: v.getConnections()){
 *			...
 *		}
 * }
 * </pre></blockquote>
 * 
 * @author Simon Rodgers
 */
public class MostConnectedVertexGraphIterator implements Iterator<Vertex> {
	
	private MostConnectedVertexGraphIterator(){} // use Builder

	// index of {vertex name : vertex} 
	private final LoadingCache<String, Vertex> index = CacheBuilder.newBuilder().build(new CacheLoader<String, Vertex>() {
		public Vertex load(String s) {
			return new Vertex(s);
		}
	});
	
	// 
	private final Map<Integer, Set<Vertex>> buckets = Maps.newTreeMap(new Comparator<Integer>() {
		@Override
		public int compare(Integer i1, Integer i2) { 
			return i2-i1; // ordered descending
		}
	});
	
	private boolean built=false;
	
	public static Builder newBuilder(){return new Builder();}
	
	@Override
	public boolean hasNext() {
		return !buckets.isEmpty();
	}

	@Override
	public Vertex next() {
		if (!hasNext())
			throw new IllegalStateException("nothing left in graph");
		
		// first entry in buckets is the bucket of the largest
		Integer largestSize = buckets.keySet().iterator().next();
		Set<Vertex> bucket = buckets.get(largestSize);
		Vertex v = bucket.iterator().next();
		
		// remove from this bucket, remove bucket if it's now empty
		bucket.remove(v);
		if (bucket.isEmpty())
			buckets.remove(largestSize);
		
		// remove connection to v from all its connections
		// then move each of those connections down a bucket
		for (Vertex other: v.getConnections()){
			int otherConnectionCount = other.getConnectionCount();
			
			// find the existing bucket for the other end of this edge
			Set<Vertex> otherOldBucket = buckets.get(otherConnectionCount); // treemap lookup takes O(log N)
			if (otherOldBucket == null)
				throw new AssertionError("bucket not found, shouldn't happen");
			
			// remove from this bucket, remove bucket if it's now empty
			otherOldBucket.remove(other);
			if (otherOldBucket.isEmpty())
				buckets.remove(otherConnectionCount);
			
			// add (create if necessary) to the next bucket down
			Set<Vertex> otherNewBucket = buckets.get(otherConnectionCount-1);
			if (null == otherNewBucket){
				otherNewBucket = Sets.newLinkedHashSet();
				buckets.put(otherConnectionCount-1, otherNewBucket);
			}
			otherNewBucket.add(other);
			
			// remove from 
			other.remove(v);
		}
		
		return v;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}
	
	public static class Builder{
		
		private Builder(){} // use Graph.newBuilder()
		
		private final MostConnectedVertexGraphIterator g = new MostConnectedVertexGraphIterator();
		
		public void addEdge(String s1, String s2){
			if (g.built)
				throw new IllegalStateException("graph already built");
			
			Vertex v1 = g.index.getUnchecked(s1);
			Vertex v2 = g.index.getUnchecked(s2);
			
			v1.add(v2);
			v2.add(v1);
		}
		
		public MostConnectedVertexGraphIterator build(){
			if (g.built)
				throw new IllegalStateException("graph already built");
			
			// for each vertex in the graph, store it in a map of {connection count : [set of all vertexes with that connection count] }
			for (Vertex v: g.index.asMap().values()){
				Set<Vertex> bucket = g.buckets.get(v.getConnectionCount());
				if (null == bucket){
					bucket = Sets.newLinkedHashSet(); // preserve input order
					g.buckets.put(v.getConnectionCount(), bucket);
				}
				bucket.add(v);
			}
			g.built=true;
			return g;
		}
	}
	
	public class Vertex {
		private final String _name;
		private final Set<Vertex> connections = Sets.newLinkedHashSet();

		public Vertex(String name) {
			_name = name;
		}
		
		public String getName(){
			return _name;
		}
		
		public Iterable<Vertex> getConnections(){
			return Collections.unmodifiableSet(connections);
		}
		
		public int getConnectionCount(){
			return connections.size();
		}
		
		private void add(Vertex other) {
			connections.add(other);
		}
		private boolean remove(Vertex v){
			return connections.remove(v);
		}

		@Override
		public int hashCode() {
			return _name.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			return _name.equals(((Vertex)o)._name);
		}
		
		@Override
		public String toString(){
			
			return _name + "->" + StringUtils.join(Collections2.transform(connections, new Function<Vertex, String>(){
				public String apply(Vertex v) {return v._name;}
			}), "");
			
//			return Array.iterableArray(connections).foldLeft(Function.curry(new F2<String, Vertex, String>(){
//				@Override
//				public String f(String a, Vertex b) {
//					return a + b._name;
//				}
//			}), _name + "->");
		}
	}

}
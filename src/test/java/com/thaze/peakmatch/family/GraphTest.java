package com.thaze.peakmatch.family;

import junit.framework.Assert;

import org.junit.Test;

public class GraphTest {
	
	@Test
	public void testGraph(){
		MostConnectedVertexGraphIterator.Builder gb = MostConnectedVertexGraphIterator.newBuilder();

		gb.addEdge("a", "b");
		gb.addEdge("a", "c");
		gb.addEdge("a", "d");
		gb.addEdge("a", "e");
		gb.addEdge("e", "f");
		gb.addEdge("e", "g");
		gb.addEdge("d", "h");
		gb.addEdge("h", "i");
		
		gb.addEdge("k", "l");
		gb.addEdge("l", "m");
		gb.addEdge("l", "n");
		
		/* 
		 * f -- e -- g
		 *      |
		 * c -- a -- b
		 *      |
		 *      d
		 *      |
		 * i -- h
		 * 
		 * k -- l -- m (unconnected graph)
		 *      |
		 *      n
		 */
		
		MostConnectedVertexGraphIterator g = gb.build();
		
		// first vertex popped will be the most connected one (a: 4)
		Assert.assertEquals("a->bcde", g.next().toString());
		
		// then the next most connected one (l: 3)
		// note that 'e', which started with a connectivity of 3, is now 2, so won't be popped yet 
		Assert.assertEquals("l->kmn", g.next().toString());
		
		// then the next set (2s)
		// (aside: note that the order within the group is deterministic but not easily predictable) 
		// (depends on whether a node started with N connections or ended up with N after a more highly connected sibling node was removed before now)
		Assert.assertEquals("h->di", g.next().toString());
		Assert.assertEquals("e->fg", g.next().toString());
		
		// etc
		Assert.assertEquals("b->", g.next().toString());
		Assert.assertEquals("c->", g.next().toString());
		Assert.assertEquals("k->", g.next().toString());
		Assert.assertEquals("m->", g.next().toString());
		Assert.assertEquals("n->", g.next().toString());
		Assert.assertEquals("d->", g.next().toString());
		Assert.assertEquals("i->", g.next().toString());
		Assert.assertEquals("f->", g.next().toString());
		Assert.assertEquals("g->", g.next().toString());
		Assert.assertFalse(g.hasNext());
		try{
			g.next();
			Assert.fail("should have thrown IllegalStateException");
		} catch (IllegalStateException e){}

//		while (g.hasNext()){
//			Vertex v = g.next();
//			System.out.println(v);
//			for (Vertex other: v.getConnections()){
//				System.out.println("\t" + other);
//			}
//		}
	}
}

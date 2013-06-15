package com.thaze.peakmatch.processors;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.thaze.peakmatch.EventProcessorConf;
import com.thaze.peakmatch.Util;
import com.thaze.peakmatch.event.Event;
import com.thaze.peakmatch.event.EventException;
import fj.Effect;
import fj.F;
import fj.F2;
import fj.P;
import fj.P2;
import fj.P3;
import fj.data.Array;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author srodgers
 *         created: 14/06/13
 */
public class ClusteringProcessor implements Processor {

	private final EventProcessorConf _conf;

	public ClusteringProcessor(EventProcessorConf conf) throws EventException {
		_conf = conf;
	}

//	@Override
//	public void processJavaML() throws EventException {
//
//		final Dataset data = new DefaultDataset();
//
//		Util.executePerEvent(_conf, new Util.EventAction() {
//			@Override
//			public void run(Event event) throws EventException {
//
//				EventAndFeatures eventAndFeatures = new EventAndFeatures(_conf, event);
//
//				Collection<Double> values = eventAndFeatures._normalisedBands.values();
//				double[] att = ArrayUtils.toPrimitive(values.toArray(new Double[0]));
//
//				data.add(new DenseInstance(att, eventAndFeatures));
//			}
//		});
//
////		KNearestNeighbors knn = new KNearestNeighbors(5);
////		knn.buildClassifier(data);
////		knn.classDistribution();
//
//
////		Clusterer c = new IterativeKMeans(3, 25, 100, new CosineDistance(), new SumOfSquaredErrors());//, new CosineDistance());
////		Clusterer c = new Cobweb(0.75, 0.1);
////		Clusterer c = new DensityBasedSpatialClustering();
//		Clusterer c = new FarthestFirst();
//
//		Dataset[] clusters = c.cluster(data);
//
//		int count=0;
//
//		for (Dataset cluster: clusters){
//			for (Instance i: cluster){
//				System.out.println(i.classValue());
//				count++;
//			}
//
//			System.out.println();
//		}
//
//		System.out.println(count);
//	}

	public void process() throws EventException {

		System.out.println("clustering ...");

		final Set<String> centreNames = Sets.newLinkedHashSet(Lists.newArrayList(_conf.getClusterCentres().split(",")));
		final List<EventAndFeatures> eventFeatures = Lists.newArrayList();
		final List<EventAndFeatures> centres = Lists.newArrayList();

		System.out.println(centreNames + " centres defined");

		Util.executePerEvent(_conf, new Util.EventAction() {
			@Override
			public void run(Event event) throws EventException {

				EventAndFeatures eaf = new EventAndFeatures(_conf, event);
				eventFeatures.add(eaf);

				if (centreNames.contains(event.getName())){
					centres.add(eaf);
				}
			}
		});

		System.out.println(centres.size() + " centres found");

		if (!centres.isEmpty()){

			class EAFScore implements Comparable<EAFScore>{
				final EventAndFeatures eaf;
				final double score;

				EAFScore(EventAndFeatures eaf, double score) {
					this.eaf = eaf;
					this.score = score;
				}

				@Override
				public int compareTo(EAFScore o) {
					return Double.compare(score, o.score); // ASCENDING - distance function ,,, smaller=better
				}
			}

			for (EventAndFeatures centre: centres){
				List<EAFScore> scores = Lists.newArrayList();
				for (EventAndFeatures eaf: eventFeatures){
					if (eaf == centre)
						continue;

					double score = DISTANCE_FN.f(centre, eaf);
					if (score <= _conf.getClusterCentreThreshold())
						scores.add(new EAFScore(eaf, score));
				}
				Collections.sort(scores);

				System.out.println();
				System.out.println(centre.toString());

				for (EAFScore eafScore: scores){
					System.out.print(eafScore.eaf.toString());
					if (_conf.isPlot1dTiny())
						System.out.println("\t" + Util.NF.format(eafScore.score));
					else
						System.out.println("\n" + " distance to first: " + Util.NF.format(eafScore.score));
				}
			}


		} else {

			Array a = Array.array(eventFeatures.toArray());

			KNNClusterer<EventAndFeatures> clusterer = new KNNClusterer<>(_conf.getClusterK(), _conf.getClusterEta(), DISTANCE_FN);
			Array<P2<EventAndFeatures,Set<EventAndFeatures>>> clusters = clusterer.cluster(a, 200);

			Set<EventAndFeatures> done = Sets.newHashSet();

			for (P2<EventAndFeatures,Set<EventAndFeatures>> cluster: clusters){

				EventAndFeatures ef1 = cluster._1();

				if (!done.contains(ef1) && !cluster._2().isEmpty()){

					System.out.println();
					System.out.println(ef1.toString());

					for (EventAndFeatures ef2: cluster._2()){
						System.out.print(ef2.toString());

						double distance = DISTANCE_FN.f(ef1, ef2);

						if (_conf.isPlot1dTiny())
							System.out.println("\t" + Util.NF.format(distance));
						else
							System.out.println("\n" + " distance to first: " + Util.NF.format(distance));

						done.add(ef2);
					}
				}

				done.add(ef1);
			}
		}
	}

	final class EventAndFeatures implements Comparable<EventAndFeatures> {
		final Event _event;
		final Map<Double, Double> _normalisedBands;
		final EventProcessorConf _conf;

		EventAndFeatures(EventProcessorConf conf, Event event){
			_event=event;
			_conf=conf;

			Map<Double, Double> bandMeans = Util.getBandMeans(_event.getD(), conf);

			// normalise to unit vector
			double sumsquares=0d;
			for (double mean: bandMeans.values())
				sumsquares += mean*mean;
			double vectorLength = Math.sqrt(sumsquares);

			_normalisedBands = Maps.newLinkedHashMap();
			for (Map.Entry<Double, Double> e: bandMeans.entrySet()){
				_normalisedBands.put(e.getKey(), e.getValue() / vectorLength);
			}
		}

		public String toString(){
			return Plot1DProcessor.formatEvent(_event, _normalisedBands, _conf, _conf.isPlot1dTiny());
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			EventAndFeatures that = (EventAndFeatures) o;

			if (!_event.getName().equals(that._event.getName())) return false;

			return true;
		}

		@Override
		public int hashCode() {
			return _event.getName().hashCode();
		}

		@Override
		public int compareTo(EventAndFeatures o) {
			return _event.getName().compareTo(o._event.getName());
		}


	}

	static final F2<EventAndFeatures, EventAndFeatures, Double> EUCLIDIAN_DISTANCE_FN = new F2<EventAndFeatures, EventAndFeatures, Double>() {
		@Override
		public Double f(EventAndFeatures e1, EventAndFeatures e2) {

			double s=0d;

			for (Map.Entry<Double, Double> e: e1._normalisedBands.entrySet()){

				Double e2Val = e2._normalisedBands.get(e.getKey());
				if (null != e2Val){
					double diff = e.getValue() - e2Val;
					s += diff*diff;
				}
			}

			return Math.sqrt(s);
		}
	};

	static final F2<EventAndFeatures, EventAndFeatures, Double> COSINE_DISTANCE_FN = new F2<EventAndFeatures, EventAndFeatures, Double>() {
		@Override
		public Double f(EventAndFeatures e1, EventAndFeatures e2) {

			double cosTheta=0d;

			for (Map.Entry<Double, Double> e: e1._normalisedBands.entrySet()){

				Double e2Val = e2._normalisedBands.get(e.getKey());
				if (null != e2Val)
					cosTheta += e.getValue() * e2Val;
			}

			return 1d-cosTheta;

//				double ret = (2 * Math.acos(cosTheta) / Math.PI);
//				System.out.println(cosTheta + " " + ret);
//				return ret;
		}
	};

	static final F2<EventAndFeatures, EventAndFeatures, Double> DISTANCE_FN = EUCLIDIAN_DISTANCE_FN;

	public class KNNClusterer<T> {

		private final int k;
		private final double eta;
		private final F2<T, T, Double> distanceFn;

		public KNNClusterer(int k, double eta, F2<T,T,Double> distanceFn) {
			this.k = k;
			this.eta = eta;
			this.distanceFn = distanceFn;
		}

		public Array<P2<T, Set<T>>> cluster(Array<T> instances, final int maxClusterSize) {

			// find the graph containing a connected subcomponent corresponding to each cluster
			UndirectedGraph<T, DefaultEdge> graph = graphify(instances);
			// extract the clusters
			final fj.data.List<Set<T>> clusters = fj.data.List.iterableList(new ConnectivityInspector<T, DefaultEdge>(graph).connectedSets());

			// format the output by assigning a (possibly empty) set of fellow members to each instance
			return instances.map( new F<T,P2<T, Set<T>>>(){
								@Override public P2<T, Set<T>> f(final T t) {
									Set<T> cluster = new HashSet<T>();
									Set<T> clusterables = clusters.find( new F<Set<T>, Boolean>(){
										@Override public Boolean f(Set<T> c) {
											return c.contains(t);
										}} )
											.orSome(Collections.<T>emptySet());
									// limit cluster sizes
									for( T instance : clusterables ) {
										if( cluster.size() == maxClusterSize ) { break; }
										if( instance != t ) {
											cluster.add(instance);
										}
									}
									return P.p(t, cluster);
								}} );
		}

		private UndirectedGraph<T, DefaultEdge> graphify(Array<T> instances) {

			final UndirectedGraph<T, DefaultEdge> graph = new SimpleGraph<T, DefaultEdge>(DefaultEdge.class);

			instances.foreach( new Effect<T>() {
				@Override public void e(T t) { graph.addVertex(t); }} );

			final java.util.List<P3<T,T,Double>> distances = calculateAllDistancePairs(instances);

			Collections.sort(distances, new Comparator<P3<T,T,Double>>(){
				@Override public int compare(P3<T, T, Double> o1, P3<T, T, Double> o2) {
					return o1._3().compareTo(o2._3());
				}});

			// for pairs relating to each instance, add an edge for any of the k nearest pairs if distance for that pair is < eta
			instances.foreach( new Effect<T>(){

				@Override public void e(T t) {
					Iterator<P3<T, T, Double>> it = distances.iterator();
					int count = 0;
					while( it.hasNext() && count < k ) {
						P3<T, T, Double> e = it.next();
						// deliberate reference equality
						if( t == e._1() || t == e._2() ) {
							count += 1;
//						it.remove(); // this is probably more trouble than it is worth
							if( e._3() < eta ) { graph.addEdge(e._1(), e._2()); }
							else { break; }
						}
					}
				}} );

			return graph;
		}

		private java.util.List<P3<T, T, Double>> calculateAllDistancePairs(Array<T> instances) {
			final int n = instances.length();
			ArrayList<P3<T,T,Double>> distances = new ArrayList<P3<T,T,Double>>( (n*(n+1))/2 );
			for( int i = 0; i < n; i++ ) {
				T t1 = instances.get(i);
				for( int j = i + 1; j < n; j++ ) {
					T t2 = instances.get(j);
					distances.add(P.p(t1, t2, distanceFn.f(t1,t2)));
				}
			}
			return distances;
		}
	}
}

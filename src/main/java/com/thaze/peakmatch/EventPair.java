package com.thaze.peakmatch;

public class EventPair implements Comparable<EventPair>{
	final Event _e1;
	final Event _e2;
	final String key;
	public EventPair(Event e1, Event e2) {

		if (e1 == e2 || e1.getName().equals(e2.getName()))
			throw new IllegalArgumentException("EventPair cannot be created from same events");

		// store events in consistent order, irrespective of the construction order
		if (e1.getName().compareTo(e2.getName()) < 0){
			_e1=e1;
			_e2=e2;
			
		} else {
			_e1=e2;
			_e2=e1;
		}
		
		key = _e1.getName() + "\t" + _e2.getName();
	}
	
	@Override
	public int compareTo(EventPair o) {
		return key.compareTo(o.key);
	}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((key == null) ? 0 : key.hashCode());
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		EventPair other = (EventPair) obj;
		if (key == null) {
			if (other.key != null)
				return false;
		} else if (!key.equals(other.key))
			return false;
		return true;
	}
}
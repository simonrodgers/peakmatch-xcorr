package com.thaze.peakmatch.event;

public class EventPair implements Comparable<EventPair>{
	private final Event _e1;
	private final Event _e2;
	private final String key;
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
		
		key = getE1().getName() + "\t" + getE2().getName();
	}
	
	@Override
	public int compareTo(EventPair o) {
		return getKey().compareTo(o.getKey());
	}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((getKey() == null) ? 0 : getKey().hashCode());
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
		if (getKey() == null) {
			if (other.getKey() != null)
				return false;
		} else if (!getKey().equals(other.getKey()))
			return false;
		return true;
	}

	public Event getE1() {
		return _e1;
	}

	public Event getE2() {
		return _e2;
	}

	public String getKey() {
		return key;
	}
}
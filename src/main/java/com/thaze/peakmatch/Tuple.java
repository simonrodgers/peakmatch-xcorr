package com.thaze.peakmatch;

public class Tuple<T, U> {
	private final T first;
	private final U second;
	
	private Tuple(T t, U u) {
		this.first = t;
		this.second = u;
	}

	public static <T, U> Tuple<T, U> tuple(T first, U second){
		return new Tuple<T,U>(first, second);
	}

	public T getFirst() {
		return first;
	}

	public U getSecond() {
		return second;
	}
	
	@Override
	public String toString(){
		return "(" + first + ", " + second + ")";
	}
}

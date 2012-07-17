package com.thaze.peakmatch.event;

public class EventException extends Exception {

	private static final long serialVersionUID = 1L; // unused

	public EventException() {
		super();
	}

	public EventException(String message, Throwable cause) {
		super(message, cause);
	}

	public EventException(String message) {
		super(message);
	}

	public EventException(Throwable cause) {
		super(cause);
	}

}

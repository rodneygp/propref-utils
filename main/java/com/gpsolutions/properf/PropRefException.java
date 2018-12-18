package com.gpsolutions.properf;

public class PropRefException extends RuntimeException {
	private static final long serialVersionUID = 8214793209568521542L;

	public PropRefException() {
		super();
	}

	public PropRefException(final String message, final Throwable cause, final boolean enableSuppression,
                            final boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public PropRefException(final String message, final Throwable cause) {
		super(message, cause);
	}

	public PropRefException(final String message) {
		super(message);
	}

	public PropRefException(final Throwable cause) {
		super(cause);
	}
}

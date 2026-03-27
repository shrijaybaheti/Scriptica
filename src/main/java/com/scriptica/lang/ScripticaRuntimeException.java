package com.scriptica.lang;

public final class ScripticaRuntimeException extends RuntimeException {
	public ScripticaRuntimeException(String message) {
		super(message);
	}

	public ScripticaRuntimeException(String message, Throwable cause) {
		super(message, cause);
	}
}


package com.surevine.sanitsation;

public class SanitisationException extends Exception {

	private static final long serialVersionUID = 1L;

	public SanitisationException(final String message) {
        super(message);
    }

    public SanitisationException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public SanitisationException(final Throwable cause) {
        super(cause);
    }

}

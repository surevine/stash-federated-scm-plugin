package com.surevine.sanitsation;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Represents the result of a sanitisation execution
 * @author jonnyheavey
 *
 */
public class SanitisationResult {

	private File archive;
	private boolean sane;
	private List<String> errors;

	public SanitisationResult(File archive, boolean sane) {
		this.archive = archive;
		this.sane = sane;
		this.errors = new ArrayList<String>();
	}

	public File getArchive() {
		return archive;
	}

	public boolean isSane() {
		return sane;
	}

	public void setSane(boolean sane) {
		this.sane = sane;
	}

	public List<String> getErrors() {
		return errors;
	}

	public void addError(String error) {
		this.errors.add(error);
	}

	public void setErrors(List<String> errors) {
		this.errors = errors;
	}

}

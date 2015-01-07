package com.surevine.sanitsation;

import java.io.File;

/**
 * Represents the result of a sanitisation execution
 * @author jonnyheavey
 *
 */
public class SanitisationResult {

	private File archive;
	private boolean sane;
	private String output;

	public SanitisationResult(File archive, boolean sane, String output) {
		this.archive = archive;
		this.sane = sane;
		this.output = output;
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

	public String getOutput() {
		return output;
	}

	public void setOutput(String output) {
		this.output = output;
	}

}

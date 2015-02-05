package com.surevine.sanitsation.service;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.surevine.sanitsation.SanitisationResult;

/**
 * Interacts with community portal sanitisation service
 * to sanitise SCM files.
 *
 * @author jonnyheavey
 */
public class SanitisationServiceFacade {

	private static final Logger LOG = LoggerFactory.getLogger(SanitisationServiceFacade.class);
	private static final int SUCCESS = 200;
	private static final int NOT_FOUND = 404;

	private static SanitisationServiceFacade _instance = null;

	private Properties config = new Properties();

	private SanitisationServiceFacade() {
		try {
			getConfig().load(getClass().getResourceAsStream("/sanitisation.properties"));
		} catch (IOException e) {
			LOG.warn("Failed to load sanitisation hook configuration.", e);
		}
	}

	public static SanitisationServiceFacade getInstance() {
		if(_instance == null) {
			_instance = new SanitisationServiceFacade();
		}
		return _instance;
	}

	/**
	 * Confirms whether archive contents are valid/clean
	 * according to sanitisation service.
	 * @param archiveToSanitise path to archive to sanitise
	 * @param projectKey project the repository belongs to
	 * @param repoSlug repository the archive of changes derive from
	 * @param identifier String identifier to describe archive source
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	public SanitisationResult isSane(Path archiveToSanitise, String projectKey, String repoSlug, String identifier) throws UnsupportedEncodingException {

		LOG.info("Sending archive to sanitisation service: " + archiveToSanitise.toString());

		File archive = new File(archiveToSanitise.toString());

		MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);
		entity.addPart("archive", new FileBody(archive));
		entity.addPart("projectKey", new StringBody(projectKey));
		entity.addPart("repoSlug", new StringBody(repoSlug));
		entity.addPart("identifier", new StringBody("Commit-"+identifier));

		String url = getConfig().getProperty("sanitisation.service.base.url") + "/sanitise";

		SanitisationResult result = new SanitisationResult(archive, false);

		try {
			HttpResponse response = Request.Post(url)
										.body(entity)
										.execute().returnResponse();

			int responseCode = response.getStatusLine().getStatusCode();

			if(responseCode != SUCCESS) {

				if(responseCode == NOT_FOUND) {
					result.addError("Project not configured for sharing in management console.");
				} else {
					result.addError(String.format("Sanitisation service failed. %s: %s",
							responseCode,
							response.getStatusLine().getReasonPhrase()));
				}

				result.setSane(false);
				return result;
			}

			String responseString = IOUtils.toString(response.getEntity().getContent(), Charset.defaultCharset());
			try {
				parseJSONResponse(result, responseString);
			} catch (JSONException e) {
				String error = "Failed to parse sanitisation service response.";
				LOG.warn(error, e);
				result.setSane(false);
				result.addError(error);
			}

		} catch (IOException e) {
			LOG.error("Failed to send archive to sanitisation service.", e);
		}

		return result;
	}

	private Properties getConfig() {
		return config;
	}

	/**
	 * Parse JSON response from sanitisation service
	 * @param result SanitisationResult to store parsed values in
	 * @param responseString raw JSON string response
	 * @throws JSONException
	 */
	private void parseJSONResponse(SanitisationResult result, String responseString) throws JSONException {
		JSONObject responseBody;

			responseBody = new JSONObject(responseString);
			result.setSane((boolean) responseBody.get("safe"));

			JSONArray errorsArray = (JSONArray) responseBody.get("errors");
			for(int i=0; i<errorsArray.length(); i++) {
				String errorMessage = (String) errorsArray.get(i);
				result.addError(errorMessage);
			}
	}

}

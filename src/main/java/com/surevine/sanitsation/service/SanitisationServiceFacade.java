package com.surevine.sanitsation.service;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
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

	private static final Logger log = LoggerFactory.getLogger(SanitisationServiceFacade.class);
	private static final int SUCCESS = 200;

	private static SanitisationServiceFacade _instance = null;

	private Properties config = new Properties();

	private SanitisationServiceFacade() {
		try {
			getConfig().load(getClass().getResourceAsStream("/sanitisation.properties"));
		} catch (IOException e) {
			log.warn("Failed to load sanitisation hook configuration.");
			e.printStackTrace();
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
	 * @param archiveToSanitise
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	public SanitisationResult isSane(Path archiveToSanitise, String projectKey, String repoSlug, String commitId) throws UnsupportedEncodingException {

		log.info("Sending archive to sanitisation service: " + archiveToSanitise.toString());

		File archive = new File(archiveToSanitise.toString());

		MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);
		entity.addPart("archive", new FileBody(archive));
		entity.addPart("projectKey", new StringBody(projectKey));
		entity.addPart("repoSlug", new StringBody(repoSlug));
		entity.addPart("commitId", new StringBody(commitId));

		String url = getConfig().getProperty("sanitisation.service.base.url") + "/sanitise";

		SanitisationResult result = new SanitisationResult(archive, false, "");

		try {
			HttpResponse response = Request.Post(url)
										.body(entity)
										.execute().returnResponse();

			if(response.getStatusLine().getStatusCode() != SUCCESS) {
				result.setSane(false);
				result.setOutput("Sanitisation service failed.");
				return result;
			}

			// Parse response from sanitisation service
			String responseString = IOUtils.toString(response.getEntity().getContent(), Charset.defaultCharset());
			JSONObject responseBody;
			try {
				responseBody = new JSONObject(responseString);
				result.setSane((boolean) responseBody.get("safe"));
				result.setOutput(responseBody.getString("message"));
			} catch (JSONException e) {
				result.setSane(false);
				result.setOutput("Failed to parse sanitisation service response.");
				return result;
			}

		} catch (IOException e) {
			log.error("Failed to send archive to sanitisation service.", e);
		}

		return result;
	}

	private Properties getConfig() {
		return config;
	}

}

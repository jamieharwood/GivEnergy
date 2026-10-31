package com.example.givenergy.client;

import org.springframework.http.HttpStatusCode;

/** Thrown when the GivEnergy API rejects the configured token (HTTP 401 or 403). */
public class GivEnergyAuthenticationException extends RuntimeException {

	private final HttpStatusCode status;

	public GivEnergyAuthenticationException(HttpStatusCode status) {
		super(messageFor(status));
		this.status = status;
	}

	public HttpStatusCode getStatus() {
		return status;
	}

	private static String messageFor(HttpStatusCode status) {
		if (status.value() == 403) {
			return "GivEnergy accepted the API token but it lacks permission for this request (HTTP 403). "
					+ "Create a token with the api:inverter:read scope at givenergy.cloud -> Account Settings -> Manage API Tokens.";
		}
		return "GivEnergy rejected the API token (HTTP 401). Check givenergy.api-token in config/application.properties "
				+ "or the GIVENERGY_API_TOKEN environment variable; the token may be mistyped, revoked or expired.";
	}

}

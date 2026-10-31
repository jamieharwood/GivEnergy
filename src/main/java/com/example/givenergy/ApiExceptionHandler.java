package com.example.givenergy;

import com.example.givenergy.client.GivEnergyAuthenticationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	/** The caller did nothing wrong; our upstream credentials did, so report a gateway error. */
	@ExceptionHandler
	ProblemDetail handleAuthentication(GivEnergyAuthenticationException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
		problem.setTitle("GivEnergy API token rejected");
		problem.setProperty("givenergyStatus", ex.getStatus().value());
		return problem;
	}

}

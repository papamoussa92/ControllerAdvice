package fr.sis.sisid.copuk.controllers;

import fr.sis.sisid.copuk.copapi.OpenBankingErrorCode;
import fr.sis.sisid.copuk.copapi.model.OBError1;
import fr.sis.sisid.copuk.copapi.model.OBErrorResponse1;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handler to provide custom implementation Responder API technical HTTP errors.
 * The handling of errors complies with the Pay.UK specification 2.3
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class CopukErrorHandlerAdvice
        extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpHeaders headers, HttpStatus status, WebRequest request) {
        log.warn("Invalid media:", ex.getContentType());
        return buildErrorResponse(OpenBankingErrorCode.HEADER_INVALID, HttpStatus.BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotAcceptable(
            HttpMediaTypeNotAcceptableException ex, HttpHeaders headers, HttpStatus status, WebRequest request) {
        log.warn("Invalid accept media:", ex.getSupportedMediaTypes());
        return buildErrorResponse(OpenBankingErrorCode.HEADER_INVALID, HttpStatus.NOT_ACCEPTABLE);
    }

    private ResponseEntity<Object> buildErrorResponse(OpenBankingErrorCode errorCode, HttpStatus status) {
        var errorReponse = new OBErrorResponse1().code(errorCode.getCode());
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>(errorReponse, responseHeaders, status);
    }

    @Override
    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatus status, WebRequest request) {

        OBErrorResponse1 errorReponse = getObErrorResponse1(ex, request);
        errorReponse.setMessage(HttpStatus.BAD_REQUEST.getReasonPhrase());
        return new ResponseEntity<>(errorReponse, headers, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(value = {
            MissingRequestHeaderException.class
    })
    protected ResponseEntity<Object> handleRequestHeaderException(MissingRequestHeaderException ex, WebRequest request) {
        return getObjectResponseEntity(ex, request, HttpStatus.BAD_REQUEST, OpenBankingErrorCode.HEADER_MISSING, HttpStatus.BAD_REQUEST.getReasonPhrase());
    }

    @ExceptionHandler(value = {
            WebClientRequestException.class
    })

    protected ResponseEntity<Object> handleExceptionInternalRemoteServer(WebClientRequestException ex, WebRequest request) {
        return getObjectResponseEntity(ex, request, HttpStatus.SERVICE_UNAVAILABLE, OpenBankingErrorCode.UNEXPECTED_ERROR, HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase());
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpHeaders headers, HttpStatus status, WebRequest request) {
        return getObjectResponseEntity(ex, request, HttpStatus.METHOD_NOT_ALLOWED, OpenBankingErrorCode.INVALID_FIELD, String.valueOf(status.value()));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatus status, WebRequest request) {
        return getObjectResponseEntity(ex, request, HttpStatus.BAD_REQUEST, OpenBankingErrorCode.INVALID_FIELD, String.valueOf(status.value()));
    }

    private OBErrorResponse1 getObErrorResponse1(MethodArgumentNotValidException ex, WebRequest request) {
        var errorReponse = new OBErrorResponse1();
        List<OBError1> obError1List = ex.getBindingResult()
                .getFieldErrors()
                .stream().map(error -> getObError1(errorReponse, request, error)).toList();
        errorReponse.setErrors(obError1List);
        return errorReponse;
    }

    private ResponseEntity<Object> getObjectResponseEntity(Exception ex, WebRequest request, HttpStatus httpStatus, OpenBankingErrorCode openBankingErrorCode, String messageCustomer) {
        log.error(ex.getMessage());
        var errorReponse = new OBErrorResponse1().code(String.valueOf(httpStatus.value()));
        errorReponse.setMessage(messageCustomer);
        OBError1 obError1 = new OBError1();
        obError1.setErrorCode(openBankingErrorCode.getCode());
        if (HttpStatus.SERVICE_UNAVAILABLE.equals(httpStatus)) {
            obError1.setMessage("Unable to reach BNP remote server");
        } else {
            obError1.setMessage(ex.getLocalizedMessage());
        }
        obError1.setPath(request.getContextPath());
        List<OBError1> obError1List = List.of(obError1);
        errorReponse.setErrors(obError1List);
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>(errorReponse, responseHeaders, httpStatus);
    }


    private OBError1 getObError1(OBErrorResponse1 errorReponse, WebRequest request,  @Nonnull FieldError error) {
        OBError1 obError1 = new OBError1();

        errorReponse.setCode(String.valueOf(HttpStatus.BAD_REQUEST.value()));

        if ("data.schemeName".equals(error.getField()) || "data.accountType".equals(
                error.getField()) || StringUtils.contains(error.getDefaultMessage(), "^[0-9]{14}$")) {
            obError1.setErrorCode(OpenBankingErrorCode.INVALID_FIELD.getCode());
            obError1.setMessage("this field " + error.getField().substring(5) + " is invalide");
        } else {
            obError1.setMessage("this field " + error.getField().substring(5)  + " " + error.getDefaultMessage());
            obError1.setErrorCode(OpenBankingErrorCode.FIELD_MISSING.getCode());
        }
        obError1.setPath(request.getContextPath());
        return obError1;
    }
}
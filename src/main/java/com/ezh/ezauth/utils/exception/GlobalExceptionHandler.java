package com.ezh.ezauth.utils.exception;

import com.ezh.ezauth.utils.common.ResponseResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CommonException.class)
    public ResponseEntity<ResponseResource<?>> handleCommonException(CommonException ex) {
        log.error("Service exception: {}", ex.getMessage());
        return new ResponseEntity<>(
                ResponseResource.error(ex.getHttpStatus(), ex.getMessage()),
                ex.getHttpStatus()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseResource<?>> handleValidationException(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.error("Validation failed: {}", errorMessage);
        return new ResponseEntity<>(
                ResponseResource.error(HttpStatus.BAD_REQUEST, errorMessage),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResponseResource<?>> handleIllegalArgument(IllegalArgumentException ex) {
        log.error("Illegal argument: {}", ex.getMessage());
        return new ResponseEntity<>(
                ResponseResource.error(HttpStatus.BAD_REQUEST, ex.getMessage()),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ResponseResource<?>> handleIllegalState(IllegalStateException ex) {
        log.error("Illegal state: {}", ex.getMessage());
        return new ResponseEntity<>(
                ResponseResource.error(HttpStatus.CONFLICT, ex.getMessage()),
                HttpStatus.CONFLICT
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResponseResource<?>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return new ResponseEntity<>(
                ResponseResource.error(HttpStatus.FORBIDDEN, "Access denied"),
                HttpStatus.FORBIDDEN
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResponseResource<?>> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        log.error("Malformed request body: {}", ex.getMessage());
        return new ResponseEntity<>(
                ResponseResource.error(HttpStatus.BAD_REQUEST, "Malformed or missing request body"),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ResponseResource<?>> handleMissingParam(MissingServletRequestParameterException ex) {
        String message = "Required parameter '" + ex.getParameterName() + "' is missing";
        log.error("Missing request parameter: {}", message);
        return new ResponseEntity<>(
                ResponseResource.error(HttpStatus.BAD_REQUEST, message),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ResponseResource<?>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value for parameter '" + ex.getName() + "'";
        log.error("Type mismatch: {}", ex.getMessage());
        return new ResponseEntity<>(
                ResponseResource.error(HttpStatus.BAD_REQUEST, message),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ResponseResource<?>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.error("Data integrity violation: {}", ex.getMessage());
        return new ResponseEntity<>(
                ResponseResource.error(HttpStatus.CONFLICT, "A record with the same unique value already exists"),
                HttpStatus.CONFLICT
        );
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ResponseResource<?>> handleNoHandler(NoHandlerFoundException ex) {
        String message = "No endpoint found for " + ex.getHttpMethod() + " " + ex.getRequestURL();
        log.warn(message);
        return new ResponseEntity<>(
                ResponseResource.error(HttpStatus.NOT_FOUND, message),
                HttpStatus.NOT_FOUND
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseResource<?>> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);
        return new ResponseEntity<>(
                ResponseResource.error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred"),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }
}

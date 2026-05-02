package com.company.inventory.exception;

import com.company.inventory.model.error.ApiError;
import com.company.inventory.model.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * Global Exception Handler class to handle all the exceptions
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handling  Business Exception like InsufficientStockException,
     * InvalidStateTransitionException, ReservationNotFoundException & SkuNotFoundException
     *@param ex BusinessException
     *@return ApiResponse
     * */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        return build(ex.errorCode(), ex.getMessage());
    }

    /**
     * Handling Validation Exception
     * @param ex MethodArgumentNotValidException
     * @return ApiResponse
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .findFirst()
                .orElse("Invalid request");
        return build(ErrorCode.INVALID_REQUEST, message);
    }
    /**
     * Bean validation for constrains Exception
     * @param ex ConstraintViolationException
     * @return ApiResponse
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleParamValidation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .findFirst().orElse("Invalid request parameter");
        return build(ErrorCode.INVALID_REQUEST, message);
    }

    /**
     * Missing pagination params for pagination page & size
     * @param ex MissingServletRequestParameterException
     * @return ApiResponse
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return build(ErrorCode.INVALID_REQUEST,
                "Required query parameter '" + ex.getParameterName() + "' is missing");
    }

    /**
     * Exception for Wrong type on a query or path parameter
     * @param ex MethodArgumentTypeMismatchException
     * @return ApiResponse
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(ErrorCode.INVALID_REQUEST,
                "Parameter '" + ex.getName() + "' has invalid value '" + ex.getValue() + "'");
    }

    /**
     * Exception for payload is not readable or compatible.
     * @param ex HttpMessageNotReadableException
     * @return ApiResponse
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(ErrorCode.INVALID_REQUEST, "Request body could not be parsed");
    }
    /**
     * Exception to handle not supported HTTP request method
     * @param ex HttpRequestMethodNotSupportedException
     * @return ApiResponse
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.failure(new ApiError("METHOD_NOT_ALLOWED", ex.getMessage())));
    }
    /**
     * Exception to handle no handler
     * @param ex NoHandlerFoundException
     * @return ApiResponse
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoHandlerFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(new ApiError("NOT_FOUND", "Endpoint not found")));
    }
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return build(ErrorCode.INVALID_STATE_TRANSITION, "Reservation was modified by a concurrent request");
    }

    /**
     * Exception to handle internal server exception
     * @param ex Exception
     * @param req WebRequest
     * @return ApiResponse
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAny(Exception ex, WebRequest req) {
        log.error("Unhandled exception", ex);
        return build(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred");
    }

    private static ResponseEntity<ApiResponse<Void>> build(ErrorCode code, String message) {
        return ResponseEntity.status(code.httpStatus())
                .headers(headers -> headers.add(HttpHeaders.CONTENT_TYPE, "application/json"))
                .body(ApiResponse.failure(new ApiError(code.name(), message)));
    }
}

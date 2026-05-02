package com.company.inventory.model.response;

import com.company.inventory.model.error.ApiError;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Class to wrap all the api response to success or failure
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(T data, ApiError error) {

    public static <T> ApiResponse<T> success(T data) { return new ApiResponse<>(data, null); }
    public static <T> ApiResponse<T> failure(ApiError error) { return new ApiResponse<>(null, error); }
}

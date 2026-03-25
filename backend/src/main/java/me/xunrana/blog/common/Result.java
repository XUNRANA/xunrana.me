package me.xunrana.blog.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Result<T> implements Serializable {

    private int code;
    private String message;
    private T data;

    public static <T> Result<T> success() {
        return Result.<T>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .message(ErrorCode.SUCCESS.getMessage())
                .build();
    }

    public static <T> Result<T> success(T data) {
        return Result.<T>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .message(ErrorCode.SUCCESS.getMessage())
                .data(data)
                .build();
    }

    public static <T> Result<T> success(String message, T data) {
        return Result.<T>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .message(message)
                .data(data)
                .build();
    }

    public static <T> Result<T> error(ErrorCode errorCode) {
        return Result.<T>builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
    }

    public static <T> Result<T> error(int code, String message) {
        return Result.<T>builder()
                .code(code)
                .message(message)
                .build();
    }
}

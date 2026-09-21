package com.tailorkz.gestao_entidades.controller.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final String MENSAGEM_GENERICA = "Ocorreu um erro interno no servidor.";

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        String mensagem = ex.getReason() != null && !ex.getReason().isBlank()
                ? ex.getReason()
                : defaultMensagem(ex.getStatusCode().value());
        return montar(ex.getStatusCode().value(), mensagem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidacao(MethodArgumentNotValidException ex) {
        String mensagem = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(erro -> erro.getField() + ": " + erro.getDefaultMessage())
                .orElse("Dados inválidos.");
        return montar(HttpStatus.BAD_REQUEST.value(), mensagem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleCorpoInvalido(HttpMessageNotReadableException ex) {
        return montar(HttpStatus.BAD_REQUEST.value(), "Corpo da requisição inválido.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleParametroObrigatorio(MissingServletRequestParameterException ex) {
        return montar(HttpStatus.BAD_REQUEST.value(), "Parâmetro obrigatório: " + ex.getParameterName() + ".");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTipoInvalido(MethodArgumentTypeMismatchException ex) {
        return montar(HttpStatus.BAD_REQUEST.value(), "Valor inválido para o parâmetro '" + ex.getName() + "'.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleTamanhoArquivo(MaxUploadSizeExceededException ex) {
        return montar(HttpStatus.PAYLOAD_TOO_LARGE.value(), "O arquivo excede o tamanho máximo permitido (30MB).");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenerico(Exception ex) {
        log.error("Erro não tratado na API", ex);
        String raiz = null;
        Throwable causa = ex;
        while (causa != null) {
            if (causa.getMessage() != null && !causa.getMessage().isBlank()) raiz = causa.getMessage();
            causa = causa.getCause();
        }
        String mensagem = raiz != null ? raiz : MENSAGEM_GENERICA;
        return montar(HttpStatus.INTERNAL_SERVER_ERROR.value(), mensagem);
    }

    private ResponseEntity<Map<String, Object>> montar(int status, String mensagem) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("timestamp", Instant.now().toString());
        corpo.put("status", status);
        corpo.put("mensagem", mensagem);
        return ResponseEntity.status(status).body(corpo);
    }

    private String defaultMensagem(int status) {
        if (status == 404) return "Recurso não encontrado.";
        if (status == 400) return "Requisição inválida.";
        if (status == 403) return "Acesso negado.";
        if (status == 401) return "Não autenticado.";
        return MENSAGEM_GENERICA;
    }
}
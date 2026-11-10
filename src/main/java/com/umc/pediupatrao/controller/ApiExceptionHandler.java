package com.umc.pediupatrao.controller;

import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converte as violações de regra de negócio das APIs em respostas JSON
 * com mensagem clara ({"erro": "..."}), em vez de erro 500.
 * Acessos negados (AccessDeniedException) seguem para o Spring Security (403).
 */
@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> dadosInvalidos(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("erro", mensagem(e)));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> operacaoNaoPermitida(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("erro", mensagem(e)));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> naoEncontrado(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", mensagem(e)));
    }

    private String mensagem(Exception e) {
        if (e instanceof NumberFormatException) {
            return "Valor numérico inválido.";
        }
        return e.getMessage() != null ? e.getMessage() : "Requisição inválida.";
    }
}

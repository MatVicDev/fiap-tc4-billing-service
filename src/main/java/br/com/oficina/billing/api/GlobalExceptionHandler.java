package br.com.oficina.billing.api;

import br.com.oficina.billing.domain.exception.DomainException;
import br.com.oficina.billing.domain.exception.EntidadeNaoEncontradaException;
import br.com.oficina.billing.infrastructure.gateway.GatewayPagamentoException;
import org.springframework.security.access.AccessDeniedException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<String> tratarDuplicado(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body("Registro duplicado — dado já existe no sistema.");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<String> tratarConcorrencia(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body("Registro alterado concorrentemente — tente novamente.");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> tratarEstadoInvalido(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(ex.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, DomainException.class})
    public ResponseEntity<String> tratarRequisicaoInvalida(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> tratarValidacao(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Dados inválidos: " + ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + " " + e.getDefaultMessage()).toList());
    }

    @ExceptionHandler(EntidadeNaoEncontradaException.class)
    public ResponseEntity<String> tratarNaoEncontrado(EntidadeNaoEncontradaException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<String> tratarAcessoNegado(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
    }

    @ExceptionHandler(GatewayPagamentoException.class)
    public ResponseEntity<String> tratarGateway(GatewayPagamentoException ex) {
        log.error("Erro no provedor de pagamento", ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> tratarErroGenerico(Exception ex) {
        log.error("Erro não tratado ao processar requisição", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro interno do servidor");
    }
}

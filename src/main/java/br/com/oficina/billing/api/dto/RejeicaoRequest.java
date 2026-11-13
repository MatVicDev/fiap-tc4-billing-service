package br.com.oficina.billing.api.dto;

import jakarta.validation.constraints.Size;

public record RejeicaoRequest(@Size(max = 300) String motivo) {
}

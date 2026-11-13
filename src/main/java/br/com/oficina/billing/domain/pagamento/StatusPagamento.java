package br.com.oficina.billing.domain.pagamento;

public enum StatusPagamento {
    PENDENTE,
    APROVADO,
    RECUSADO,
    ESTORNADO;

    public boolean isFinal() {
        return this != PENDENTE;
    }
}

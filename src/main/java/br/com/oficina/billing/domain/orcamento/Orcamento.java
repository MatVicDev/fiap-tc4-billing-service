package br.com.oficina.billing.domain.orcamento;

import br.com.oficina.billing.domain.exception.DomainException;
import jakarta.persistence.*;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@NoArgsConstructor
@Table(name = "orcamentos", indexes = @Index(name = "idx_orcamento_os", columnList = "osId", unique = true))
public class Orcamento {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID osId;

    private UUID clienteId;
    private String nomeCliente;
    private String emailCliente;

    @Column(length = 14)
    private String cpfCliente;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "itens_orcamento", joinColumns = @JoinColumn(name = "orcamento_id"))
    private List<ItemOrcamento> itens = new ArrayList<>();

    @Column(nullable = false)
    private BigDecimal valorTotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusOrcamento status;

    @Column(length = 500)
    private String motivo;

    private LocalDateTime dataGeracao;
    private LocalDateTime dataDecisao;

    @Version
    private Long versao;

    public Orcamento(UUID osId, UUID clienteId, String nomeCliente, String emailCliente, String cpfCliente,
                     List<ItemOrcamento> itens) {
        if (osId == null) {
            throw new DomainException("Orçamento precisa estar vinculado a uma OS.");
        }
        if (itens == null || itens.isEmpty()) {
            throw new DomainException("Orçamento precisa ter ao menos um item.");
        }
        this.id = UUID.randomUUID();
        this.osId = osId;
        this.clienteId = clienteId;
        this.nomeCliente = nomeCliente;
        this.emailCliente = emailCliente;
        this.cpfCliente = cpfCliente;
        this.itens.addAll(itens);
        this.valorTotal = itens.stream().map(ItemOrcamento::getPrecoTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (valorTotal.signum() <= 0) {
            throw new DomainException("Valor total do orçamento deve ser positivo.");
        }
        this.status = StatusOrcamento.PENDENTE;
        this.dataGeracao = LocalDateTime.now();
    }

    public void aprovar() {
        exigir(StatusOrcamento.PENDENTE);
        this.status = StatusOrcamento.APROVADO;
        this.dataDecisao = LocalDateTime.now();
    }

    public void rejeitar(String motivo) {
        exigir(StatusOrcamento.PENDENTE);
        this.status = StatusOrcamento.REJEITADO;
        this.motivo = motivo;
        this.dataDecisao = LocalDateTime.now();
    }

    /** Pagamento confirmado. Se o orçamento já foi cancelado, mantém o status (o orquestrador pedirá estorno). */
    public void registrarPagamento() {
        if (status == StatusOrcamento.APROVADO) {
            this.status = StatusOrcamento.PAGO;
        }
    }

    /**
     * Compensação da Saga. Idempotente. Orçamento já pago não é cancelado aqui (retorna
     * false): o orquestrador, ao receber o pagamento-confirmado, solicitará o estorno.
     */
    public boolean cancelar(String motivo) {
        if (status == StatusOrcamento.PENDENTE || status == StatusOrcamento.APROVADO) {
            this.status = StatusOrcamento.CANCELADO;
            this.motivo = motivo;
            return true;
        }
        return false;
    }

    public void registrarEstorno(String motivo) {
        this.status = StatusOrcamento.ESTORNADO;
        this.motivo = motivo;
    }

    public boolean pertenceAoCpf(String cpf) {
        return cpfCliente != null && cpfCliente.equals(cpf);
    }

    private void exigir(StatusOrcamento esperado) {
        if (status != esperado) {
            throw new IllegalStateException("Orçamento está " + status + " — operação permitida apenas quando " + esperado + ".");
        }
    }

    public UUID getId() { return id; }
    public UUID getOsId() { return osId; }
    public UUID getClienteId() { return clienteId; }
    public String getNomeCliente() { return nomeCliente; }
    public String getEmailCliente() { return emailCliente; }
    public String getCpfCliente() { return cpfCliente; }
    public List<ItemOrcamento> getItens() { return Collections.unmodifiableList(itens); }
    public BigDecimal getValorTotal() { return valorTotal; }
    public StatusOrcamento getStatus() { return status; }
    public String getMotivo() { return motivo; }
    public LocalDateTime getDataGeracao() { return dataGeracao; }
    public LocalDateTime getDataDecisao() { return dataDecisao; }
}

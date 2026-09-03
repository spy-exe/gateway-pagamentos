package br.com.ricardofigueiredo.gateway.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Estabelecimento que usa o gateway. Toda cobranca pertence a um usuario
 * e so pode ser lida ou alterada por ele.
 */
@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Column(name = "nome_estabelecimento", nullable = false)
    private String nomeEstabelecimento;

    @Column(name = "chave_pix")
    private String chavePix;

    private String cidade;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected Usuario() {
    }

    public Usuario(String email, String senhaHash, String nomeEstabelecimento,
                   String chavePix, String cidade) {
        this.email = email;
        this.senhaHash = senhaHash;
        this.nomeEstabelecimento = nomeEstabelecimento;
        this.chavePix = chavePix;
        this.cidade = cidade;
        this.criadoEm = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public String getNomeEstabelecimento() {
        return nomeEstabelecimento;
    }

    public String getChavePix() {
        return chavePix;
    }

    public String getCidade() {
        return cidade;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}

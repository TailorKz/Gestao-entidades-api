package com.tailorkz.gestao_entidades.config;

import com.tailorkz.gestao_entidades.domain.enums.Categoria;
import com.tailorkz.gestao_entidades.domain.enums.Role;
import com.tailorkz.gestao_entidades.domain.model.Fomento;
import com.tailorkz.gestao_entidades.domain.model.Parcela;
import com.tailorkz.gestao_entidades.domain.model.Tenant;
import com.tailorkz.gestao_entidades.domain.model.Usuario;
import com.tailorkz.gestao_entidades.domain.repository.FomentoRepository;
import com.tailorkz.gestao_entidades.domain.repository.ParcelaRepository;
import com.tailorkz.gestao_entidades.domain.repository.TenantRepository;
import com.tailorkz.gestao_entidades.domain.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final TenantRepository tenantRepository;
    private final UsuarioRepository usuarioRepository;
    private final FomentoRepository fomentoRepository;
    private final ParcelaRepository parcelaRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.ativo:true}")
    private boolean ativo;

    @Value("${app.seed.tenant-nome:INDACI}")
    private String tenantNome;

    @Value("${app.seed.tenant-cnpj:00.000.000/0001-00}")
    private String tenantCnpj;

    @Value("${app.seed.admin-login:admin}")
    private String adminLogin;

    @Value("${app.seed.admin-senha:admin123}")
    private String adminSenha;

    @Value("${app.seed.fomento-ano:2026}")
    private int fomentoAno;

    @Value("${app.seed.parcela-numero:1}")
    private int parcelaNumero;

    @Value("${app.seed.parcela-valor:85000.00}")
    private BigDecimal parcelaValor;

    public DataSeeder(TenantRepository tenantRepository,
                      UsuarioRepository usuarioRepository,
                      FomentoRepository fomentoRepository,
                      ParcelaRepository parcelaRepository,
                      PasswordEncoder passwordEncoder) {
        this.tenantRepository = tenantRepository;
        this.usuarioRepository = usuarioRepository;
        this.fomentoRepository = fomentoRepository;
        this.parcelaRepository = parcelaRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!ativo) {
            log.info("Seed desativado (app.seed.ativo=false).");
            return;
        }
        semear();
    }

    private void semear() {
        Tenant tenant = tenantRepository.findByCnpj(tenantCnpj)
                .orElseGet(() -> {
                    Tenant novo = Tenant.builder()
                            .nome(tenantNome)
                            .cnpj(tenantCnpj)
                            .build();
                    log.info("Seed: criando tenant '{}' com CNPJ '{}'", tenantNome, tenantCnpj);
                    return tenantRepository.save(novo);
                });

        semearAdmin(tenant);
        semearFomento(tenant);
    }

    private void semearAdmin(Tenant tenant) {
        if (usuarioRepository.findByLogin(adminLogin).isPresent()) {
            return;
        }
        Usuario admin = Usuario.builder()
                .tenant(tenant)
                .nome("Administrador INDACI")
                .login(adminLogin)
                .senhaHash(passwordEncoder.encode(adminSenha))
                .role(Role.SUPER_ADMIN)
                .precisaTrocarSenha(true)
                .build();
        usuarioRepository.save(admin);
        log.info("Seed: usuário '{}' criado (primeiro acesso, troque a senha).", adminLogin);
    }

    private void semearFomento(Tenant tenant) {
        for (Categoria cat : List.of(Categoria.ESPORTE, Categoria.CULTURA)) {
            Fomento fomento = fomentoRepository.findFirstByTenantIdAndCategoria(tenant.getId(), cat)
                    .orElseGet(() -> {
                        String titulo = (cat == Categoria.ESPORTE ? "Convênio Esporte " : "Convênio Cultura ") + fomentoAno;
                        Fomento novo = Fomento.builder()
                                .tenant(tenant)
                                .titulo(titulo)
                                .valorTotal(parcelaValor)
                                .anoVigencia(fomentoAno)
                                .categoria(cat)
                                .build();
                        log.info("Seed: criando fomento '{}' (categoria {}).", titulo, cat);
                        return fomentoRepository.save(novo);
                    });

            if (!parcelaRepository.findByFomento_TenantIdAndFomento_Categoria(tenant.getId(), cat).isEmpty()) {
                continue;
            }

            Parcela parcela = Parcela.builder()
                    .fomento(fomento)
                    .numero(parcelaNumero)
                    .valorInicial(parcelaValor)
                    .saldoAtual(parcelaValor)
                    .mesesReferencia("Janeiro, Fevereiro, Março, Abril, Maio, Junho, Julho, Agosto")
                    .build();
            parcelaRepository.save(parcela);
            log.info("Seed: parcela 0{} criada no valor de R$ {} para {}.", parcelaNumero, parcelaValor, cat);
        }
    }
}
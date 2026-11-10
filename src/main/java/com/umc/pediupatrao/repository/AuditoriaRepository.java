package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Auditoria;
import java.util.List;
import org.springframework.data.repository.Repository;

/**
 * Repositório da auditoria. Estende apenas Repository (e não MongoRepository)
 * para expor somente gravação e consulta: não há métodos de exclusão.
 */
public interface AuditoriaRepository extends Repository<Auditoria, String> {

    Auditoria save(Auditoria auditoria);

    List<Auditoria> findAllByOrderByDataHoraDesc();
}

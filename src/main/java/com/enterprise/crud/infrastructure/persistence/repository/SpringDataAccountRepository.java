package com.enterprise.crud.infrastructure.persistence.repository;

import com.enterprise.crud.infrastructure.persistence.entity.AccountJpaEntity;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataAccountRepository extends JpaRepository<AccountJpaEntity, UUID> {

    Optional<AccountJpaEntity> findByDocumentNumber(String documentNumber);

    boolean existsByDocumentNumber(String documentNumber);

    List<AccountJpaEntity> findAllByOrderByIdAsc(Limit limit);

    List<AccountJpaEntity> findByIdGreaterThanOrderByIdAsc(UUID cursor, Limit limit);
}

package org.ohdsi.webapi.algorithm.repository;

import com.cosium.spring.data.jpa.entity.graph.domain.EntityGraph;
import com.cosium.spring.data.jpa.entity.graph.repository.EntityGraphJpaRepository;

import org.ohdsi.webapi.algorithm.CustomAlgorithm;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomAlgorithmRepository extends EntityGraphJpaRepository<CustomAlgorithm, Integer> {
    
    CustomAlgorithm findById(Integer id, EntityGraph entityGraph);
    @Query("SELECT COUNT(pa) FROM CustomAlgorithm pa WHERE pa.name = :name and pa.id <> :id")
    int getCountAlgorithmsWithSameName(@Param("id") Integer id, @Param("name") String name);
}
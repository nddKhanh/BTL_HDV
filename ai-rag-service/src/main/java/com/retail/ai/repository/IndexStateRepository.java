package com.retail.ai.repository;

import com.retail.ai.entity.IndexState;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class IndexStateRepository implements PanacheRepositoryBase<IndexState, Short> {
    public IndexState current() {
        return findById((short) 1);
    }
}

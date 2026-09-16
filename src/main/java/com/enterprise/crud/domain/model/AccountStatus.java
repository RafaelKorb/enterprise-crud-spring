package com.enterprise.crud.domain.model;

public enum AccountStatus {

    ACTIVE {
        @Override
        public boolean canTransitionTo(AccountStatus target) {
            return target == BLOCKED || target == CLOSED;
        }
    },
    BLOCKED {
        @Override
        public boolean canTransitionTo(AccountStatus target) {
            return target == ACTIVE || target == CLOSED;
        }
    },
    CLOSED {
        @Override
        public boolean canTransitionTo(AccountStatus target) {
            return false;
        }
    };

    public abstract boolean canTransitionTo(AccountStatus target);
}

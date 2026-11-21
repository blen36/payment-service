package com.innowise.paymentservice.service;

import com.innowise.paymentservice.exception.PaymentApiException;

public record PaymentAccess(long userId, boolean admin) {
    public void requireOwner(long ownerId) {
        if (!admin && ownerId != userId) throw PaymentApiException.forbidden();
    }
    public Long listOwner(Long requestedOwner) {
        if (admin) return requestedOwner;
        if (requestedOwner != null) requireOwner(requestedOwner);
        return userId;
    }
    public void requireAdmin() {
        if (!admin) throw PaymentApiException.forbidden();
    }
}

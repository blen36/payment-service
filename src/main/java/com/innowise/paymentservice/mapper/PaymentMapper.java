package com.innowise.paymentservice.mapper;

import com.innowise.paymentservice.client.OrderClient.OrderSnapshot;
import com.innowise.paymentservice.dto.PaymentDtos.*;
import com.innowise.paymentservice.entity.Payment;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PaymentMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "timestamp", ignore = true)
    @Mapping(target = "orderId", source = "request.orderId")
    @Mapping(target = "userId", source = "order.user.id")
    @Mapping(target = "paymentAmount", source = "order.totalPrice")
    Payment toEntity(CreatePayment request, OrderSnapshot order);
    PaymentView toView(Payment payment);
}

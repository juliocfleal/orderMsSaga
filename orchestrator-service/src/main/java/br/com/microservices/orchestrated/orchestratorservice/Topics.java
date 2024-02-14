package br.com.microservices.orchestrated.orchestratorservice;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Topics {

    START_SAGA("start-saga"),
    BASE_ORCHESTRATOR("base-orchestrator"),
    FINISH_SUCCESS("finish-success"),
    FINISH_FAIL("finish-fail"),
    PRODUCT_VALIDATION_SUCCESS("product-validation-success"),
    PRODUCT_VALIDATION_FAIL("product-validation-fail"),
    PAYMENT_VALIDATION_SUCCESS("payment-success"),
    PAYMENT_VALIDATION_FAIL("payment-fail"),
    INVENTORY_VALIDATION_SUCCESS("inventory-success"),
    INVENTORY_VALIDATION_FAIL("inventory-fail"),
    NOTIFY_ENDING("notify-ending");

    private String topic;
}

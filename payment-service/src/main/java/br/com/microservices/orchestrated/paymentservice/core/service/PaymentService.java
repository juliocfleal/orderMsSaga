package br.com.microservices.orchestrated.paymentservice.core.service;

import br.com.microservices.orchestrated.paymentservice.config.exception.ValidationException;
import br.com.microservices.orchestrated.paymentservice.core.dto.Event;
import br.com.microservices.orchestrated.paymentservice.core.dto.History;
import br.com.microservices.orchestrated.paymentservice.core.dto.OrderProducts;
import br.com.microservices.orchestrated.paymentservice.core.enums.EPaymentStatus;
import br.com.microservices.orchestrated.paymentservice.core.model.Payment;
import br.com.microservices.orchestrated.paymentservice.core.producer.KafkaProducer;
import br.com.microservices.orchestrated.paymentservice.core.repository.PaymentRepository;
import br.com.microservices.orchestrated.paymentservice.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static br.com.microservices.orchestrated.paymentservice.core.enums.ESagaStatus.*;

@Slf4j
@Service
@AllArgsConstructor
public class PaymentService {

    private static final String CURRENT_SOURCE = "PAYMENT_SERVICE";
    private static Double REDUCE_SUM_VALUE= 0.0;
    private static Double MIN_AMOUT_VALUE = 0.1;


    private final JsonUtil jsonUtil;
    private final KafkaProducer producer;
    private final PaymentRepository paymentRepository;

    public void realizePayment(Event event) {
        try {
            checkCurrentValidation(event);
            createPendingPayment(event);
            var payment = findByOrderIdAndTransactionId(event);
            validateAmount(payment.getTotalAmount());
            changePaymentToSuccess(payment);
            handleSuccess(event);
        } catch (Exception exception) {
            log.error("Error trying to make payment: ", exception);
            handleFailCurrentNotExecuted(event,exception.getMessage());
        }
        producer.sendEvent(jsonUtil.toJson(event));
    }

    private void changePaymentToSuccess(Payment payment){
        payment.setStatus(EPaymentStatus.SUCCESS);
        save(payment);

    }

    private void validateAmount(double amount){
        if(amount < MIN_AMOUT_VALUE){
            throw new ValidationException("The minimun amount available is ".concat(MIN_AMOUT_VALUE.toString()));
        }
    }

    private Payment findByOrderIdAndTransactionId(Event event){
        return paymentRepository.findByOrderIdAndTransactionId(event.getOrderId(), event.getTransactionId())
                .orElseThrow( () -> new ValidationException("Payment not found by orderId and transactionId"));
    }
    private void setEventAmountItens(Event event, Payment payment){
        event.getPayload().setTotalAmount(payment.getTotalAmount());
        event.getPayload().setTotalItems(payment.getTotalItems());
    }
    private void createPendingPayment(Event event) {
        var totalAmount = calculateAmount(event);
        var totalItens = calculateTotalItens(event);
        var payment = Payment.builder()
                .orderId(event.getOrderId())
                .transactionId(event.getTransactionId())
                .totalAmount(totalAmount)
                .totalItems(totalItens)
                .build();
        save(payment);
        setEventAmountItens(event,payment);
    }

    private void save(Payment payment) {
        paymentRepository.save(payment);
    }

    private double calculateAmount(Event event) {
        return event.getPayload().getProducts().stream().map(product ->
                product.getQuantity() * product.getProduct().getUnitValue()
        ).reduce(REDUCE_SUM_VALUE, Double::sum);
    }

    private int calculateTotalItens(Event event) {
        return event.getPayload().getProducts().stream().map(OrderProducts::getQuantity
        ).reduce(REDUCE_SUM_VALUE.intValue(), Integer::sum);
    }

    private void checkCurrentValidation(Event event) {
        if (paymentRepository.existsByOrderIdAndTransactionId(event.getOrderId(), event.getTransactionId())) {
            throw new ValidationException("There is another transactionId for this validation.");
        }
    }

    private void handleSuccess(Event event) {
        event.setStatus(SUCCESS);
        event.setSource(CURRENT_SOURCE);
        addHistory(event, "Payment realized successfully!");
    }

    private void addHistory(Event event, String message) {
        var history = History.builder()
                .source(event.getSource())
                .status(event.getStatus())
                .message(message)
                .createdAt(LocalDateTime.now())
                .build();
        event.addToHistory(history);
    }

    private void handleFailCurrentNotExecuted(Event event, String message) {
        event.setStatus(ROLLBACK_PENDING);
        event.setSource(CURRENT_SOURCE);
        addHistory(event, "Fail to realize payment ".concat(message));
    }

    public void realizeRefund(Event event){
        event.setStatus(FAIL);
        event.setSource(CURRENT_SOURCE);

        try {
            changePaymentStatusToRefund(event);
            addHistory(event, "Rollback executed for payment");
        }catch (Exception exception){
            addHistory(event, "Rollback not executed for payment: ".concat(exception.getMessage()));

        }
        producer.sendEvent(jsonUtil.toJson(event));
    }

    private void changePaymentStatusToRefund(Event event){
        var payment = findByOrderIdAndTransactionId(event);
        payment.setStatus(EPaymentStatus.REFUND);
        setEventAmountItens(event, payment);
        save(payment);
    }
}

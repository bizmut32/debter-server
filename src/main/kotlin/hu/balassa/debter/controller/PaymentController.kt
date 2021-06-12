package hu.balassa.debter.controller

import hu.balassa.debter.dto.request.AddPaymentRequest
import hu.balassa.debter.service.PaymentService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import javax.validation.Valid

@RestController
@RequestMapping("/room/{roomKey}/payment")
class PaymentController (
    private val service: PaymentService
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun addPayment(
        @PathVariable roomKey: String,
        @RequestBody @Valid request: AddPaymentRequest
    ) = service.addPayment(request, roomKey)
}
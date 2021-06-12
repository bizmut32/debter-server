package hu.balassa.debter.service

import hu.balassa.debter.client.CurrencyConverterClient
import hu.balassa.debter.dto.request.AddPaymentRequest
import hu.balassa.debter.mapper.ModelDtoMapper
import hu.balassa.debter.model.Payment
import hu.balassa.debter.repository.DebterRepository
import hu.balassa.debter.util.generateUUID
import hu.balassa.debter.util.useRoom
import org.springframework.stereotype.Service

@Service
class PaymentService(
    private val repository: DebterRepository,
    private val mapper: ModelDtoMapper,
    private val exchangeClient: CurrencyConverterClient,
    private val debtArrangementService: DebtArrangementService
) {
    fun addPayment(request: AddPaymentRequest, roomKey: String) = repository.useRoom(roomKey) { room ->
        val payer = room.members.find { it.id == request.memberId }
            ?: throw IllegalArgumentException("Invalid member id: ${request.memberId}")
        require(request.included.all { included -> room.members.any { it.id == included } }) { "Invalid member id in included" }

        val id = generateUUID()
        val convertedValue = exchangeClient.convert(request.currency, room.currency, request.value)
        val payment = mapper.addPaymentRequestToPayment(request, id, convertedValue)

        payer.payments = mutableListOf<Payment>().apply { addAll(payer.payments); add(payment) }

        debtArrangementService.arrangeDebts(room)
    }

}
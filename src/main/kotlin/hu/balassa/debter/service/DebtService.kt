package hu.balassa.debter.service

import hu.balassa.debter.model.DebtArrangement
import hu.balassa.debter.model.Member
import hu.balassa.debter.model.Payment
import hu.balassa.debter.model.Room
import org.springframework.stereotype.Service

@Service
open class DebtService {

    open fun arrangeDebts(room: Room) {
        val memberDebts = getMemberDebts(room.members)

        val (claims, debts) = memberDebts.partition { it.debt < 0.0 }
        val debtArranger = DebtArranger(claims, debts, room.rounding)
        val arrangements = debtArranger.arrange()

        setMemberDebts(room, arrangements)
    }

    private fun setMemberDebts(room: Room, arrangements: List<SimpleDebtArrangement>) {
        val debts = arrangements.groupBy { it.fromId }
        room.members.forEach { member ->
            debts[member.id]?.let { memberDebts ->
                member.debts = memberDebts.map {
                    DebtArrangement().apply {
                        payeeId = it.toId
                        value = it.amount
                        currency = room.currency
                        arranged = false
                    }
                }
            }
        }
    }


    private fun getMemberDebts(members: List<Member>): List<SimpleMember> {
        val simplePayments = paymentsToSimplePayment(members)

        val memberSums = members.associate { it.id to 0.0 } + simplePayments
            .groupBy { it.memberId }
            .mapValues { payment -> payment.value.sumByDouble { it.value } }

        val roomSum = memberSums.map { it.value }.sum()

        val preferredAmount = roomSum / members.size
        return memberSums.map { (memberId, sum) -> SimpleMember(memberId, preferredAmount - sum) }
    }

    private fun paymentsToSimplePayment(members: List<Member>): List<SimplePayment> {
        val memberIds = members.map { it.id }
        return members
            .flatMap { member -> member.payments.map { member.id to it } }
            .filter { it.second.active }
            .flatMap { paymentToSimplePayments(it.first, it.second, memberIds) }

    }

    private fun paymentToSimplePayments(payerId: String, payment: Payment, members: List<String>): List<SimplePayment> {
        val excludedMembers = members.filter { it !in payment.includedMemberIds }
        return mutableListOf(SimplePayment(payerId, payment.convertedValue)).apply {
            addAll(excludedMembers.map {
                val amount = payment.convertedValue / payment.includedMemberIds.size
                SimplePayment(it, amount)
            })
        }
    }
}

data class SimplePayment(val memberId: String, val value: Double)
data class SimpleMember(val memberId: String, var debt: Double)
data class SimpleDebtArrangement (val fromId: String, val toId: String, val amount: Double)
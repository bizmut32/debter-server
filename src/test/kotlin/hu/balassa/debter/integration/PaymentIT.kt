package hu.balassa.debter.integration

import hu.balassa.debter.dto.response.RoomDetailsResponse
import hu.balassa.debter.model.Currency.HUF
import hu.balassa.debter.model.DebtArrangement
import hu.balassa.debter.model.Room
import hu.balassa.debter.service.DebtService
import hu.balassa.debter.util.dateOf
import hu.balassa.debter.util.responseBody
import hu.balassa.debter.util.testDebt
import hu.balassa.debter.util.testMember
import hu.balassa.debter.util.testPayment
import hu.balassa.debter.util.testRoom
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.byLessThan
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.test.mock.mockito.MockBean
import java.time.temporal.ChronoUnit.MINUTES

class PaymentIT: BaseIT() {
    companion object {
        private const val ROOM_KEY = "ROOMID"
        private const val PAYMENT_ID = "member1payment1"
    }

    @Test
    fun addPayment() {
        whenever(repository.findByKey(ROOM_KEY)).thenReturn(testRoom(ROOM_KEY))

        val response = web.post().uri("room/$ROOM_KEY/payment")
            .bodyValue(object {
                val value = 10.0
                val memberId = "member1"
                val currency = "HUF"
                val note = "test note"
                val date = "2020-09-12T12:30:00+02:00"
                val included = listOf("member1", "member2")
            })
            .exchange()
            .expectStatus().isCreated
            .responseBody<RoomDetailsResponse>()

        assertThat(response.payments).anySatisfy {
            assertThat(it.memberId).isEqualTo("member1")
            assertThat(it.value).isEqualTo(10.0)
            assertThat(it.realValue).isEqualTo(10.0)
            assertThat(it.currency).isEqualTo(HUF)
            assertThat(it.date).isCloseTo(dateOf(2020, 9, 12, 12, 30), byLessThan(1, MINUTES))
            assertThat(it.included).containsExactly("member1", "member2")
            assertThat(it.note).isEqualTo("test note")
            assertThat(it.active).isTrue
        }

        assertThat(response.debts.sumByDouble { it.value }).isEqualTo(response.members.sumByDouble { it.debt })

        argumentCaptor<Room> {
            verify(repository).save(capture())
            assertThat(firstValue.key).isEqualTo(ROOM_KEY)
            assertThat(firstValue.members.find { it.id == "member1" }!!.payments).hasSize(3).anySatisfy {
                assertThat(it.value).isEqualTo(10.0)
                assertThat(it.convertedValue).isEqualTo(10.0)
                assertThat(it.currency).isEqualTo(HUF)
                assertThat(it.date).isCloseTo(dateOf(2020, 9, 12, 12, 30), byLessThan(1, MINUTES))
                assertThat(it.includedMemberIds).containsExactly("member1", "member2")
                assertThat(it.note).isEqualTo("test note")
                assertThat(it.active).isTrue
            }
        }
    }

    @Test
    fun addPaymentWithForeignCurrency() {
        whenever(repository.findByKey(ROOM_KEY)).thenReturn(testRoom(ROOM_KEY))

        val response = web.post().uri("room/$ROOM_KEY/payment")
            .bodyValue(object {
                val value = 10.0
                val memberId = "member1"
                val currency = "EUR"
                val note = "test note"
                val date = "2020-09-12T12:30:00+02:00"
                val included = listOf("member1", "member2")
            })
            .exchange()
            .expectStatus().isCreated
            .responseBody<RoomDetailsResponse>()

        assertThat(response.payments).anySatisfy {
            assertThat(it.value).isEqualTo(10.0)
            assertThat(it.realValue).isCloseTo(3481.8, Offset.offset(0.1))
        }
        argumentCaptor<Room> {
            verify(repository).save(capture())
            assertThat(firstValue.key).isEqualTo(ROOM_KEY)
            assertThat(firstValue.members.find { it.id == "member1" }!!.payments).hasSize(3).anySatisfy {
                assertThat(it.value).isEqualTo(10.0)
                assertThat(it.convertedValue).isCloseTo(3481.8, Offset.offset(0.1))
            }
        }
    }

    @Test
    fun addPaymentResolveDebt() {
        val room = testRoom(rounding = 10.0, members = listOf(
            testMember(id = "member1", debts = listOf(testDebt(value = 100.0, payeeId = "member2"))),
            testMember(id = "member2", debts = emptyList())))
        whenever(repository.findByKey(ROOM_KEY)).thenReturn(room)

        val response = web.post().uri("room/$ROOM_KEY/payment")
            .bodyValue(object {
                val value = 97.0
                val memberId = "member1"
                val currency = "HUF"
                val note = "test note"
                val date = "2020-09-12T12:30:00+02:00"
                val included = listOf("member2")
            })
            .exchange()
            .expectStatus().isCreated
            .responseBody<RoomDetailsResponse>()

        assertThat(response.debts).hasSize(1).allMatch { it.arranged }
    }


    @Test
    fun deletePayment() {
        whenever(repository.findByKey(ROOM_KEY)).thenReturn(testRoom(ROOM_KEY))

        val response = web.delete().uri("room/$ROOM_KEY/payment/$PAYMENT_ID")
            .exchange()
            .expectStatus().isOk
            .responseBody<RoomDetailsResponse>()

        assertThat(response.payments).anySatisfy {
            assertThat(it.id).isEqualTo(PAYMENT_ID)
            assertThat(it.active).isFalse
        }

        argumentCaptor<Room> {
            verify(repository).save(capture())
            assertThat(firstValue.members.find { it.id == "member1" }!!.payments).anySatisfy {
                assertThat(it.id).isEqualTo(PAYMENT_ID)
                assertThat(it.active).isFalse
            }
        }
    }

    @Test
    fun revivePayment() {
        whenever(repository.findByKey(ROOM_KEY)).thenReturn(testRoom(
            key = ROOM_KEY,
            members = listOf(testMember(id="member1", payments = listOf(testPayment("member1payment1", active = false))))
        ))

        val response = web.patch().uri("room/$ROOM_KEY/payment/$PAYMENT_ID")
            .exchange()
            .expectStatus().isOk
            .responseBody<RoomDetailsResponse>()

        assertThat(response.payments).anySatisfy {
            assertThat(it.id).isEqualTo(PAYMENT_ID)
            assertThat(it.active).isTrue
        }
        argumentCaptor<Room> {
            verify(repository).save(capture())
            assertThat(firstValue.members.find { it.id == "member1" }!!.payments).anySatisfy {
                assertThat(it.id).isEqualTo(PAYMENT_ID)
                assertThat(it.active).isTrue
            }
        }
    }
}
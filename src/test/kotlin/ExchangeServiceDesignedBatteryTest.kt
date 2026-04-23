package com.example.exchange

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifySequence
import org.iesra.revilofe.ExchangeRateProvider
import org.iesra.revilofe.ExchangeService
import org.iesra.revilofe.InMemoryExchangeRateProvider
import org.iesra.revilofe.Money

class ExchangeServiceDesignedBatteryTest : DescribeSpec({

    afterTest {
        clearAllMocks()
    }

    describe("battery designed from equivalence classes for ExchangeService") {

        describe("input validation") {
            val provider = mockk<ExchangeRateProvider>()
            val service = ExchangeService(provider)

            it("throws an exception when the amount is zero") {
                // Cantidad igual a cero
                shouldThrow<IllegalArgumentException> {
                    service.exchange(Money(0, "USD"), "EUR")
                }
            }

            it("throws an exception when the amount is negative") {
                // Cantidad negativa
                shouldThrow<IllegalArgumentException> {
                    service.exchange(Money(-50, "USD"), "EUR")
                }
            }

            it("throws an exception when the source currency code is invalid") {
                // Moneda origen con longitud distinta de 3
                shouldThrow<IllegalArgumentException> {
                    service.exchange(Money(100, "US"), "EUR")
                }
            }

            it("throws an exception when the target currency code is invalid") {
                // Moneda destino con longitud distinta de 3
                shouldThrow<IllegalArgumentException> {
                    service.exchange(Money(100, "USD"), "EU")
                }
            }
        }

        describe("Misma moneda origen y destino") {
            val realProvider = InMemoryExchangeRateProvider(mapOf("USDEUR" to 0.92))
            val provider = spyk(realProvider)
            val service = ExchangeService(provider)

            it("Devuelve la misma cantidad si origen y destino son iguales.") {
                val result = service.exchange(Money(500, "USD"), "USD")
                result shouldBe 500L
                verify(exactly = 0) { provider.rate(any()) }
            }
        }

        describe("conversión con tasa directa") {
            val provider = mockk<ExchangeRateProvider>()
            val service = ExchangeService(provider)

            it("Convierte correctamente usando una tasa directa con stub") {
                every { provider.rate("USDEUR") } returns 0.92
                val result = service.exchange(Money(200, "USD"), "EUR")
                result shouldBe 184L
            }
        }

        describe("Spy sobre InMemoryExchangeRateProvider para verificar una llamada real correcta.") {
            val realProvider = InMemoryExchangeRateProvider(
                mapOf("USDEUR" to 0.92, "EURUSD" to 1.08)
            )
            val provider = spyk(realProvider)
            val service = ExchangeService(provider)

            it("consulta al proveedor exactamente una vez con el par correcto") {
                val result = service.exchange(Money(300, "USD"), "EUR")
                result shouldBe 276L

                verify(exactly = 1) { provider.rate("USDEUR") }
                confirmVerified(provider)
            }
        }

        describe("conversión cruzada") {
            val provider = mockk<ExchangeRateProvider>()
            val service = ExchangeService(provider, setOf("USD", "EUR", "GBP", "JPY"))

            it("Resolver una conversión cruzada cuando la tasa directa no exista usando mock") {
                // Sin tasa directa pero con ruta disponible
                every { provider.rate("USDJPY") } throws IllegalArgumentException("not found")
                every { provider.rate("USDEUR") } returns 0.92
                every { provider.rate("EURJPY") } returns 160.0

                val result = service.exchange(Money(100, "USD"), "JPY")
                result shouldBe 14720L
            }

            it("Intenta una segunda ruta intermedia si la primera falla usando mock") {
                // Primera ruta falla a medias, segunda ruta funciona
                every { provider.rate("USDGBP") } throws IllegalArgumentException("not found")
                // Primera
                every { provider.rate("USDEUR") } returns 0.92
                every { provider.rate("EURGBP") } throws IllegalArgumentException("not found")
                // Segunda
                every { provider.rate("USDJPY") } returns 110.0
                every { provider.rate("JPYGBP") } returns 0.0065

                val result = service.exchange(Money(100, "USD"), "GBP")
                result shouldBe 71L
            }

            it("lanza una excepción si no existe ninguna ruta válida") {
                // Todas las rutas fallan
                every { provider.rate(any()) } throws IllegalArgumentException("not found")

                shouldThrow<IllegalArgumentException> {
                    service.exchange(Money(100, "USD"), "EUR")
                }
            }

            it("Verifica el orden exacto de las llamadas al proveedor en una conversión cruzada") {
                every { provider.rate("USDGBP") } throws IllegalArgumentException("not found")
                every { provider.rate("USDEUR") } returns 0.92
                every { provider.rate("EURGBP") } returns 0.86

                service.exchange(Money(50, "USD"), "GBP")

                verifySequence {
                    provider.rate("USDGBP")
                    provider.rate("USDEUR")
                    provider.rate("EURGBP")
                }
                confirmVerified(provider)
            }
        }
    }
})
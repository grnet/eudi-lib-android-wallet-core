/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.europa.ec.eudi.wallet.card

import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.UnsignedDocument
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcClaim
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcData
import eu.europa.ec.eudi.wallet.document.metadata.IssuerMetadata
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// GRNET fork: the card display of a WE BUILD SCA-Card (DPC), rb-sca-card-dpc §2.9.
class CardDisplayTest {

    private fun document(network: String?, credentialDisplay: String?): IssuedDocument {
        val metadata = IssuerMetadata(
            documentConfigurationIdentifier = "card",
            display = emptyList(),
            claims = null,
            credentialIssuerIdentifier = "https://issuer.example",
            issuerDisplay = null,
            credentialDisplay = credentialDisplay?.let { Json.decodeFromString<List<JsonObject>>(it) },
        )
        val networkClaim = network?.let {
            mockk<SdJwtVcClaim> {
                every { claimName } returns "network"
                every { value } returns JsonPrimitive(it)
            }
        }
        val data = mockk<SdJwtVcData> { every { claims } returns listOfNotNull(networkClaim) }
        return mockk<IssuedDocument> {
            every { issuerMetadata } returns metadata
            every { this@mockk.data } returns data
        }
    }

    private val sample = """
        [{
          "card": {
            "type": { "code": "CREDIT", "label": "Credit card" },
            "alias": "Gold Mastercard",
            "last_four": "1234",
            "card_art": [
              { "theme": "DARK", "image_url": "https://bank.example/dark.png" },
              { "theme": "DEFAULT", "image_url": "https://bank.example/card.png" },
              { "theme": "LIGHT", "image_url": "http://bank.example/insecure.png" }
            ],
            "issuer": { "branding": { "name": "Partner Bank" } },
            "network_branding": { "network": "mastercard", "branding": { "name": "Mastercard" } }
          }
        }]
    """.trimIndent()

    @Test
    fun `the card is shown by its alias, last four digits, type, network, issuer and card art`() {
        val card = CardDisplay.of(document("mastercard", sample))

        assertEquals("Gold Mastercard", card?.title)
        assertEquals("1234", card?.lastFour)
        assertEquals("•••• 1234", card?.maskedNumber)
        assertEquals(CardDisplay.Type("CREDIT", "Credit card"), card?.type)
        assertEquals("Mastercard", card?.networkName)
        assertEquals("Partner Bank", card?.issuerName)
    }

    @Test
    fun `the card art matches the theme, else is the default one, and is never plain HTTP`() {
        val cardArt = CardDisplay.of(document("mastercard", sample))!!.cardArt

        assertEquals("https://bank.example/dark.png", cardArt.forTheme(darkTheme = true))
        assertEquals("https://bank.example/card.png", cardArt.forTheme(darkTheme = false))
        assertEquals("https://bank.example/card.png", cardArt.anyTheme)
    }

    @Test
    fun `a data URL is a valid image`() {
        val dataUrl = "data:image/png;base64,iVBORw0KGgo="
        val card = CardDisplay.of(document("mastercard", sample.replace("https://bank.example/card.png", dataUrl)))

        assertEquals(dataUrl, card?.cardArt?.anyTheme)
    }

    @Test
    fun `without an alias the network's branding name is the title`() {
        val card = CardDisplay.of(document("mastercard", sample.replace("\"alias\": \"Gold Mastercard\",", "")))

        assertEquals("Mastercard", card?.title)
    }

    @Test
    fun `a display whose network differs from the signed claim falls back to the claim (IR-04)`() {
        val card = CardDisplay.of(document("visa", sample))

        assertEquals(CardDisplay("Visa", networkName = "Visa"), card)
    }

    @Test
    fun `a display without network branding is valid`() {
        val withoutBranding = sample.replace(
            ",\n    \"network_branding\": { \"network\": \"mastercard\", \"branding\": { \"name\": \"Mastercard\" } }",
            "",
        )
        check(!withoutBranding.contains("network_branding"))

        val card = CardDisplay.of(document("mastercard", withoutBranding))

        assertEquals("Gold Mastercard", card?.title)
        assertEquals("•••• 1234", card?.maskedNumber)
        assertEquals("https://bank.example/card.png", card?.cardArt?.anyTheme)
        assertEquals("Mastercard", card?.networkName)
    }

    @Test
    fun `last_four that is not four digits is not shown`() {
        val card = CardDisplay.of(document("mastercard", sample.replace("\"1234\"", "\"12a4\"")))

        assertNull(card?.lastFour)
        assertNull(card?.maskedNumber)
    }

    @Test
    fun `a document without a card display has none`() {
        assertNull(CardDisplay.of(document("mastercard", credentialDisplay = null)))
        assertNull(CardDisplay.of(document("mastercard", """[{ "name": "PID" }]""")))
        assertNull(CardDisplay.of(mockk<UnsignedDocument>()))
    }
}

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

package eu.europa.ec.eudi.wallet.dcapi.registration

import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcData
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Locale

/**
 * GRNET fork: how a payment card is shown in the credential selector, from the display meta-data
 * of a WE BUILD SCA-Card (DPC) attestation (rb-sca-card-dpc §2.9): its `card` object, which the
 * issuer delivers in the credential response's display array.
 *
 * @property title the card's name, e.g. "Gold Mastercard"
 * @property subtitle the last four digits of the card number, e.g. "•••• 1234", if known
 * @property cardArtUrl the card art, an HTTPS or `data:` URL, if any
 */
internal data class CardDisplay(
    val title: String,
    val subtitle: String?,
    val cardArtUrl: String?,
) {
    companion object {

        /**
         * The card display of [document], or `null` if the issuer delivered none.
         *
         * The title is the card's `alias`, else the network's branding name. When the display's
         * `network_branding.network` differs from the attestation's signed `network` claim, the
         * display is invalid (IR-04) and only the network claim is shown. A display without network
         * branding, which the rulebook's schema allows, is valid.
         */
        fun of(document: IssuedDocument): CardDisplay? {
            val card = document.issuerMetadata?.credentialDisplay
                ?.firstNotNullOfOrNull { it["card"] as? JsonObject }
                ?: return null

            val networkClaim = (document.data as? SdJwtVcData)?.claims
                ?.find { it.claimName == "network" }
                ?.value
                ?.let { (it as? JsonPrimitive)?.contentOrNull ?: it as? String }
            val networkBranding = card.obj("network_branding")
            val brandingNetwork = networkBranding?.string("network")
            if (networkClaim != null && brandingNetwork != null && brandingNetwork != networkClaim) {
                return CardDisplay(networkClaim.capitalized(), subtitle = null, cardArtUrl = null)
            }

            val title = card.string("alias")
                ?: networkBranding?.obj("branding")?.string("name")
                ?: networkClaim?.capitalized()
                ?: return null
            val subtitle = card.string("last_four")
                ?.takeIf { it.length == 4 && it.all(Char::isDigit) }
                ?.let { "•••• $it" }
            return CardDisplay(title, subtitle, cardArtUrl = card.imageUrl("card_art"))
        }

        /**
         * The image for any theme: `DEFAULT`, else `LIGHT`, else the first. The selector is drawn
         * in either theme and takes a single icon.
         */
        private fun JsonObject.imageUrl(name: String): String? {
            val images = (this[name] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: return null
            val image = images.find { it.string("theme") == "DEFAULT" }
                ?: images.find { it.string("theme") == "LIGHT" }
                ?: images.firstOrNull()
            return image?.string("image_url")
        }

        private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

        private fun JsonObject.string(name: String): String? =
            (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

        private fun String.capitalized(): String =
            replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }
}

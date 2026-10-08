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

import eu.europa.ec.eudi.wallet.document.Document
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcData
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Locale

/**
 * GRNET fork: how a payment card is shown, in the wallet and in the credential selector, from the
 * display meta-data of a WE BUILD SCA-Card (DPC) attestation (rb-sca-card-dpc §2.9): its `card`
 * object, which the issuer delivers in the credential response's display array. The object is
 * unsigned and for display only.
 *
 * ```
 * val card = CardDisplay.of(document) ?: return // not a payment card
 * val art = CardArtStore(context).get(card.cardArt.forTheme(darkTheme = isSystemInDarkTheme()))
 * ```
 *
 * @property title the card's name, e.g. "Gold Mastercard"
 * @property lastFour the last four digits of the card number, if known
 * @property type the card's product type, e.g. `CREDIT` with the label "Credit card", if known
 * @property networkName the payment network's name, e.g. "Mastercard"
 * @property issuerName the card issuer's name, e.g. "Partner Bank", if known
 * @property cardArt the card art
 * @property networkLogo the payment network's logo
 * @property issuerLogo the card issuer's logo
 */
data class CardDisplay(
    val title: String,
    val lastFour: String? = null,
    val type: Type? = null,
    val networkName: String? = null,
    val issuerName: String? = null,
    val cardArt: Images = Images(),
    val networkLogo: Images = Images(),
    val issuerLogo: Images = Images(),
) {

    /** The last four digits as the card number is shown, e.g. "•••• 1234", if known. */
    val maskedNumber: String? get() = lastFour?.let { "•••• $it" }

    /**
     * A card product type.
     *
     * @property code `CREDIT`, `DEBIT` or `PREPAID`
     * @property label its name for the user, e.g. "Credit card", if the issuer gave one
     */
    data class Type(val code: String, val label: String?)

    /**
     * An image in its theme variants, each an HTTPS or `data:` URL; images in other schemes are
     * left out.
     *
     * @property variants the URL for each theme: `DEFAULT`, `LIGHT` or `DARK`
     */
    data class Images(val variants: List<Pair<String, String>> = emptyList()) {

        /**
         * The image for the active UI theme, else the `DEFAULT` one (rb-sca-card-dpc §4), else
         * any, or `null` if there is none.
         */
        fun forTheme(darkTheme: Boolean): String? =
            url(if (darkTheme) "DARK" else "LIGHT") ?: url("DEFAULT") ?: variants.firstOrNull()?.second

        /**
         * The image for a surface drawn in either theme, such as the credential selector:
         * `DEFAULT`, else `LIGHT`, else any.
         */
        val anyTheme: String?
            get() = url("DEFAULT") ?: url("LIGHT") ?: variants.firstOrNull()?.second

        private fun url(theme: String): String? = variants.find { it.first == theme }?.second
    }

    companion object {

        /**
         * The card display of [document], or `null` if it is not an issued document or its issuer
         * delivered none.
         *
         * The title is the card's `alias`, else the network's branding name. When the display's
         * `network_branding.network` differs from the attestation's signed `network` claim, the
         * display is invalid (IR-04) and only the network claim is shown. A display without network
         * branding, which the rulebook's schema allows, is valid.
         */
        @JvmStatic
        fun of(document: Document): CardDisplay? {
            if (document !is IssuedDocument) return null
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
                return CardDisplay(networkClaim.capitalized(), networkName = networkClaim.capitalized())
            }

            val networkName = networkBranding?.obj("branding")?.string("name")
                ?: networkClaim?.capitalized()
            val title = card.string("alias") ?: networkName ?: return null
            val issuerBranding = card.obj("issuer")?.obj("branding")
            return CardDisplay(
                title = title,
                lastFour = card.string("last_four")?.takeIf { it.length == 4 && it.all(Char::isDigit) },
                type = card.obj("type")?.let { type ->
                    type.string("code")?.let { Type(it, type.string("label")) }
                },
                networkName = networkName,
                issuerName = issuerBranding?.string("name"),
                cardArt = card.images("card_art"),
                networkLogo = networkBranding?.obj("branding").images("logo"),
                issuerLogo = issuerBranding.images("logo"),
            )
        }

        private fun JsonObject?.images(name: String): Images =
            Images(
                (this?.get(name) as? JsonArray).orEmpty()
                    .mapNotNull { it as? JsonObject }
                    .mapNotNull { image ->
                        val url = image.string("image_url")
                            ?.takeIf { it.startsWith("https://") || it.startsWith("data:") }
                        url?.let { (image.string("theme") ?: "DEFAULT") to it }
                    }
            )

        private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

        private fun JsonObject.string(name: String): String? =
            (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

        private fun String.capitalized(): String =
            replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }
}

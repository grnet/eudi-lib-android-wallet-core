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

import eu.europa.ec.eudi.wallet.dcapi.registration.DCAPICredentialRegistry.Companion.selectorTitle
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

// GRNET fork: the title of a credential in Android's selector, from DCAPIConfig.credentialTitles.
class DCAPICredentialRegistryTitleTest {

    private val titles = mapOf(
        "urn:eudi:pid:1" to "Person Identification Data",
        "eu.europa.ec.eudi.pid.1" to "Person Identification Data",
    )

    private fun document(name: String, format: Any): IssuedDocument = mockk {
        every { this@mockk.name } returns name
        every { this@mockk.format } returns when (format) {
            is SdJwtVcFormat -> format
            is MsoMdocFormat -> format
            else -> error("format")
        }
    }

    @Test
    fun `the title given for the document's vct is used`() {
        val pid = document("PID (SD-JWT VC)", SdJwtVcFormat(vct = "urn:eudi:pid:1"))
        assertEquals("Person Identification Data", pid.selectorTitle(titles))
    }

    @Test
    fun `the title given for the document's docType is used`() {
        val pid = document("PID (MSO Mdoc)", MsoMdocFormat(docType = "eu.europa.ec.eudi.pid.1"))
        assertEquals("Person Identification Data", pid.selectorTitle(titles))
    }

    @Test
    fun `a document of another type keeps its name`() {
        val dpc = document("SCA Card (DPC)", SdJwtVcFormat(vct = "https://issuer.example/dpc/1.0"))
        assertEquals("SCA Card (DPC)", dpc.selectorTitle(titles))
        assertEquals("SCA Card (DPC)", dpc.selectorTitle(emptyMap()))
    }
}

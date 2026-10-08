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

import android.content.Context
import android.graphics.BitmapFactory
import eu.europa.ec.eudi.wallet.internal.e
import eu.europa.ec.eudi.wallet.logging.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Base64

/**
 * GRNET fork: the images of payment cards ([CardDisplay]), downloaded once and kept on the
 * device, so that the credential selector and the wallet's own screens show the same image
 * without fetching it each time.
 *
 * An image is downloaded the first time it is asked for, without cookies or caches and with a
 * generic `User-Agent` instead of the platform's, which names the device model: the rulebook asks
 * wallets to avoid identifying headers when fetching images (rb-sca-card-dpc §4). A `data:` URL is
 * decoded, not stored. Every instance shares the same files.
 *
 * @param context the context whose no-backup files directory holds the images
 * @param logger optional logger
 * @param ioDispatcher the dispatcher for file and network I/O
 */
class CardArtStore @JvmOverloads constructor(
    context: Context,
    private val logger: Logger? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val directory = File(context.noBackupFilesDir, DIRECTORY)

    /**
     * The image at [url], an HTTPS or base64 `data:` URL, or `null` if there is none or it cannot
     * be read as an image.
     */
    suspend fun get(url: String?): ByteArray? = withContext(ioDispatcher) {
        when {
            url == null -> null
            url.startsWith("data:") -> decodeDataUrl(url)?.takeIf { it.isImage() }
            url.startsWith("https://") -> {
                val file = fileFor(url)
                file.takeIf { it.isFile }?.readBytes()
                    ?: download(url)?.takeIf { it.isImage() }?.also { store(file, it) }
            }

            else -> null
        }
    }

    /** Deletes the stored images other than those at [urls], e.g. those of deleted cards. */
    suspend fun retainOnly(urls: Collection<String>) = withContext(ioDispatcher) {
        val kept = urls.map { fileFor(it).name }.toSet()
        directory.listFiles()?.filter { it.name !in kept }?.forEach { it.delete() }
    }

    private fun fileFor(url: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        return File(directory, digest.joinToString("") { "%02x".format(it) })
    }

    /** Written to a temporary file first, so that a concurrent reader never sees half an image. */
    private fun store(file: File, bytes: ByteArray) {
        try {
            directory.mkdirs()
            val temporary = File.createTempFile(file.name, ".tmp", directory)
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(file)) temporary.delete()
        } catch (e: Exception) {
            logger?.e(TAG, "Failed to store the card image", e)
        }
    }

    private fun download(url: String): ByteArray? = try {
        (URL(url).openConnection() as HttpURLConnection).run {
            try {
                connectTimeout = 5_000
                readTimeout = 10_000
                useCaches = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "image/*")
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    logger?.e(TAG, "Failed to download the card image from $url: HTTP $responseCode")
                    null
                } else {
                    inputStream.use { it.readAtMost(MAX_BYTES) }
                }
            } finally {
                disconnect()
            }
        }
    } catch (e: Exception) {
        logger?.e(TAG, "Failed to download the card image from $url", e)
        null
    }

    /** The whole stream, or `null` if it is longer than [limit] bytes. */
    private fun InputStream.readAtMost(limit: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = read(buffer)
            if (read < 0) return output.toByteArray()
            if (output.size() + read > limit) {
                logger?.e(TAG, "The card image is larger than $limit bytes")
                return null
            }
            output.write(buffer, 0, read)
        }
    }

    private fun decodeDataUrl(url: String): ByteArray? = try {
        val (header, content) = url.removePrefix("data:").split(",", limit = 2)
        require(header.endsWith(";base64")) { "Only base64 data URLs are supported" }
        Base64.getDecoder().decode(content)
    } catch (e: Exception) {
        logger?.e(TAG, "Failed to read the card image's data URL", e)
        null
    }

    private fun ByteArray.isImage(): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(this, 0, size, options)
        return options.outWidth > 0 && options.outHeight > 0
    }

    private companion object {
        const val TAG = "CardArtStore"
        const val DIRECTORY = "grnet-card-art"
        const val USER_AGENT = "EUDI Wallet"
        const val MAX_BYTES = 2 * 1024 * 1024
    }
}

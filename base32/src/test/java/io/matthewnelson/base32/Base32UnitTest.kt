package io.matthewnelson.base32

import com.google.common.io.BaseEncoding
import okio.internal.commonToUtf8String
import org.junit.Assert
import org.junit.Test
import java.io.CharArrayWriter
import java.security.SecureRandom

/**
 * Tests decode/encode output against Guava
 * */
class Base32UnitTest {

    private val sRandom: SecureRandom = SecureRandom()
    private val testDepth: Int = 1_000

    @Test
    fun `Okio base32 _decode_ matches Guava output`() {
        val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray()

        for (i in 0 until testDepth) {

            val charArrayWriter = CharArrayWriter(i)

            // Build character array of randomly chosen base32 characters that varies in size
            repeat(i) {
                charArrayWriter.append(base32Chars[sRandom.nextInt(base32Chars.size)])
            }

            charArrayWriter.toString().let { randomEncoding ->

                // Base32 (A-Z and 2-7)
                randomEncoding.decodeBase32ToArray(Base32.Default)?.let { okioDecoded ->

                    val guavaDecoded = BaseEncoding.base32().decode(randomEncoding)
                    Assert.assertEquals(okioDecoded.size, guavaDecoded.size)
                    Assert.assertEquals(okioDecoded.commonToUtf8String(), guavaDecoded.commonToUtf8String())

                } ?: let {
                    // Okio base32 returned null, so Guava BaseEncoding should
                    // throw an exception for the given string value 'randomEncoding'
                    try {
                        BaseEncoding.base32().decode(randomEncoding)

                        // Fail test if IllegalArgumentException is not thrown
                        Assert.fail()
                    } catch (e: IllegalArgumentException) {
                        // Expected exception. Pass
                    }
                }
            }
        }
    }

    @Test
    fun `Okio base32hex _decode_ matches Guava output`() {
        val base32Chars = "0123456789ABCDEFGHIJKLMNOPQRSTUV".toCharArray()

        for (i in 0 until testDepth) {

            val charArrayWriter = CharArrayWriter(i)

            // Build character array of randomly chosen base32 characters that varies in size
            repeat(i) {
                charArrayWriter.append(base32Chars[sRandom.nextInt(base32Chars.size)])
            }

            charArrayWriter.toString().let { randomEncoding ->

                // Base32Hex (0-9 and A-V)
                randomEncoding.decodeBase32ToArray(Base32.Hex)?.let { okioDecoded ->

                    val guavaDecoded = BaseEncoding.base32Hex().decode(randomEncoding)
                    Assert.assertEquals(okioDecoded.size, guavaDecoded.size)
                    Assert.assertEquals(okioDecoded.commonToUtf8String(), guavaDecoded.commonToUtf8String())

                } ?: let {
                    // Okio base32 returned null, so Guava BaseEncoding should
                    // throw an exception for the given string value 'randomEncoding'
                    try {
                        BaseEncoding.base32Hex().decode(randomEncoding)

                        // Fail test if IllegalArgumentException is not thrown
                        Assert.fail()
                    } catch (e: IllegalArgumentException) {
                        // Expected exception. Pass
                    }
                }
            }
        }
    }

    @Test
    fun `Okio base32 _encode_ matches Guava output`() {
        for (i in 0 until testDepth) {
            val randomByteArray = ByteArray(i)

            // Build ByteArray of randomly chosen bytes
            for (j in 0 until i) {
                randomByteArray[j] = sRandom.nextInt().toByte()
            }

            randomByteArray.encodeBase32().let { okioEncoded ->
                BaseEncoding.base32().encode(randomByteArray).let { guavaEncoded ->
                    Assert.assertEquals(okioEncoded, guavaEncoded)
                }
            }
        }
    }
}
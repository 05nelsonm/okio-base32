package io.matthewnelson.base32

import okio.ByteString.Companion.encodeUtf8
import okio.internal.commonToUtf8String

sealed class Base32 {

    internal abstract val encodingTable: ByteArray

    object Crockford: Base32() {
        override val encodingTable: ByteArray
            get() = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".encodeUtf8().toByteArray()
    }

    object Default: Base32() {
        override val encodingTable: ByteArray
            get() = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".encodeUtf8().toByteArray()
    }

    object Hex: Base32() {
        override val encodingTable: ByteArray
            get() = "0123456789ABCDEFGHIJKLMNOPQRSTUV".encodeUtf8().toByteArray()
    }
}

fun String.decodeBase32ToArray(type: Base32 = Base32.Default): ByteArray? {
    var limit: Int = length

    // If encoding type is Crockford, ensure all characters are uppercase
    val stringToDecode: String = if (type is Base32.Crockford) {
        this.toUpperCase()
    } else {
        this
    }

    // Disregard padding and/or whitespace from end of input
    while (limit > 0) {
        val c = stringToDecode[limit - 1]
        if (c != '=' && c != '\n' && c != '\r' && c != ' ' && c != '\t') {
            break
        }
        limit--
    }

    val out: ByteArray = ByteArray((limit * 5L / 8L).toInt())
    var outCount: Int = 0
    var inCount: Int = 0

    var bitBuffer: Long = 0L
    for (i in 0 until limit) {
        val bits: Long = when (val c: Char = stringToDecode[i]) {
            in 'A'..'Z' -> {
                when (type) {
                    is Base32.Crockford -> {
                        when (c) {
                            in 'A'..'H' -> {
                                // char ASCII value
                                //  A    65    10
                                //  H    72    17 (ASCII - 55)
                                c.toLong() - 55L
                            }
                            'I', 'L' -> {
                                // Crockford treats characters 'I', 'i', 'L' and 'l' as 1

                                // char ASCII value
                                //  1    49    1 (ASCII - 48)
                                '1'.toLong() - 48L
                            }
                            'J', 'K' -> {
                                // char ASCII value
                                //  J    74    18
                                //  K    75    19 (ASCII - 56)
                                c.toLong() - 56L
                            }
                            'M', 'N' -> {
                                // char ASCII value
                                //  M    77    20
                                //  N    78    21 (ASCII - 57)
                                c.toLong() - 57L
                            }
                            'O' -> {
                                // Crockford treats characters 'O' and 'o' as 0

                                // char ASCII value
                                //  0    48    0 (ASCII - 48)
                                '0'.toLong() - 48L
                            }
                            in 'P'..'T' -> {
                                // char ASCII value
                                //  P    80    22
                                //  T    84    26 (ASCII - 58)
                                c.toLong() - 58L
                            }
                            'U' -> {
                                // Crockford excludes 'U' and 'u'
                                return null
                            }
                            else -> { // Remaining characters are V-Z
                                // char ASCII value
                                //  V    86    27
                                //  Z    90    31 (ASCII - 59)
                                c.toLong() - 59L
                            }
                        }
                    }
                    is Base32.Default -> {
                        // char ASCII value
                        //  A    65    0
                        //  Z    90    25 (ASCII - 65)
                        c.toLong() - 65L
                    }
                    is Base32.Hex -> {

                        // base32Hex uses A-V only
                        if (c in 'W'..'Z') {
                            return null
                        }

                        // char ASCII value
                        //  A    65    10
                        //  V    86    31 (ASCII - 55)
                        c.toLong() - 55L
                    }
                }
            }
            in '0'..'9' -> {
                when (type) {
                    is Base32.Default -> {

                        // Default base32 uses 2-7 only
                        if (c in '0'..'1' || c in '8'..'9') {
                            return null
                        }

                        // char ASCII value
                        //  2    50    26
                        //  7    55    31 (ASCII - 24)
                        c.toLong() - 24L
                    }
                    is Base32.Crockford,
                    is Base32.Hex -> {
                        // char ASCII value
                        //  0    48    0
                        //  9    57    9 (ASCII - 48)
                        c.toLong() - 48L
                    }
                }
            }
            '\n', '\r', ' ', '\t' -> {
                continue
            }
            else -> {
                // Crockford allows insertion of hyphens which we ignore when decoding
                if (type is Base32.Crockford && c == '-') {
                    continue
                }

                return null
            }
        }

        // Append this char's 5 bits to the buffer
        bitBuffer = bitBuffer shl 5 or bits

        // For every 8 chars of input, we accumulate 40 bits of output data. Emit 5 bytes
        inCount++
        if (inCount % 8 == 0) {
            out[outCount++] = (bitBuffer shr 32).toByte()
            out[outCount++] = (bitBuffer shr 24).toByte()
            out[outCount++] = (bitBuffer shr 16).toByte()
            out[outCount++] = (bitBuffer shr  8).toByte()
            out[outCount++] = (bitBuffer       ).toByte()
        }
    }

    when (inCount % 8) {
        0 -> {}
        1, 3, 6 -> {
            // 5*1 = 5 bits.  Truncated, fail.
            // 5*3 = 15 bits. Truncated, fail.
            // 5*6 = 30 bits. Truncated, fail.
            return null
        }
        2 -> { // 5*2 = 10 bits. Drop 2
            bitBuffer = bitBuffer shr 2
            out[outCount++] = bitBuffer.toByte()
        }
        4 -> { // 5*4 = 20 bits. Drop 4
            bitBuffer = bitBuffer shr 4
            out[outCount++] = (bitBuffer shr 8).toByte()
            out[outCount++] = (bitBuffer      ).toByte()
        }
        5 -> { // 5*5 = 25 bits. Drop 1
            bitBuffer = bitBuffer shr 1
            out[outCount++] = (bitBuffer shr 16).toByte()
            out[outCount++] = (bitBuffer shr  8).toByte()
            out[outCount++] = (bitBuffer       ).toByte()
        }
        7 -> { // 5*7 = 35 bits. Drop 3
            bitBuffer = bitBuffer shr 3
            out[outCount++] = (bitBuffer shr 24).toByte()
            out[outCount++] = (bitBuffer shr 16).toByte()
            out[outCount++] = (bitBuffer shr  8).toByte()
            out[outCount++] = (bitBuffer       ).toByte()
        }
    }

    return if (outCount == out.size) {
        out
    } else {
        out.copyOf(outCount)
    }
}

fun ByteArray.encodeBase32(type: Base32 = Base32.Default): String {
    val base32Lookup: ByteArray = type.encodingTable
    val length: Long = (size.toLong() / 5L * 8L) + if (size % 5 == 0) 0L else 8L
    val out = ByteArray(length.toInt())

    var index = 0
    val end = size - size % 5
    var i = 0

    while (i < end) {
        var bitBuffer: Long = 0L

        repeat(5) {
            bitBuffer = (bitBuffer shl 8) + this.retrieveBits(i++)
        }

        out[index++] = base32Lookup[(bitBuffer shr 35 and 0x1fL).toInt()] // 40-1*5 = 35
        out[index++] = base32Lookup[(bitBuffer shr 30 and 0x1fL).toInt()] // 40-2*5 = 30
        out[index++] = base32Lookup[(bitBuffer shr 25 and 0x1fL).toInt()] // 40-3*5 = 25
        out[index++] = base32Lookup[(bitBuffer shr 20 and 0x1fL).toInt()] // 40-4*5 = 20
        out[index++] = base32Lookup[(bitBuffer shr 15 and 0x1fL).toInt()] // 40-5*5 = 15
        out[index++] = base32Lookup[(bitBuffer shr 10 and 0x1fL).toInt()] // 40-6*5 = 10
        out[index++] = base32Lookup[(bitBuffer shr  5 and 0x1fL).toInt()] // 40-7*5 = 5
        out[index++] = base32Lookup[(bitBuffer        and 0x1fL).toInt()] // 40-8*5 = 0
    }

    var bitBuffer: Long = 0L
    when (size - end) {
        0 -> {}
        1 -> { // 8*1 = 8 bits
            bitBuffer = (bitBuffer shl 8) + this.retrieveBits(i)
            out[index++] = base32Lookup[(bitBuffer shr 3 and 0x1fL).toInt()] // 8-1*5 = 3
            out[index++] = base32Lookup[(bitBuffer shl 2 and 0x1fL).toInt()] // 5-3 = 2
            out[index++] = '='.toByte()
            out[index++] = '='.toByte()
            out[index++] = '='.toByte()
            out[index++] = '='.toByte()
            out[index++] = '='.toByte()
            out[index]   = '='.toByte()
        }
        2 -> { // 8*2 = 16 bits
            bitBuffer = (bitBuffer shl 8) + this.retrieveBits(i++)
            bitBuffer = (bitBuffer shl 8) + this.retrieveBits(i)
            out[index++] = base32Lookup[(bitBuffer shr 11 and 0x1fL).toInt()] // 16-1*5 = 11
            out[index++] = base32Lookup[(bitBuffer shr  6 and 0x1fL).toInt()] // 16-2*5 = 6
            out[index++] = base32Lookup[(bitBuffer shr  1 and 0x1fL).toInt()] // 16-3*5 = 1
            out[index++] = base32Lookup[(bitBuffer shl  4 and 0x1fL).toInt()] // 5-1 = 4
            out[index++] = '='.toByte()
            out[index++] = '='.toByte()
            out[index++] = '='.toByte()
            out[index]   = '='.toByte()
        }
        3 -> { // 8*3 = 24 bits
            bitBuffer = (bitBuffer shl 8) + this.retrieveBits(i++)
            bitBuffer = (bitBuffer shl 8) + this.retrieveBits(i++)
            bitBuffer = (bitBuffer shl 8) + this.retrieveBits(i)
            out[index++] = base32Lookup[(bitBuffer shr 19 and 0x1fL).toInt()] // 24-1*5 = 19
            out[index++] = base32Lookup[(bitBuffer shr 14 and 0x1fL).toInt()] // 24-2*5 = 14
            out[index++] = base32Lookup[(bitBuffer shr  9 and 0x1fL).toInt()] // 24-3*5 = 9
            out[index++] = base32Lookup[(bitBuffer shr  4 and 0x1fL).toInt()] // 24-4*5 = 4
            out[index++] = base32Lookup[(bitBuffer shl  1 and 0x1fL).toInt()] // 5-4 = 1
            out[index++] = '='.toByte()
            out[index++] = '='.toByte()
            out[index]   = '='.toByte()
        }
        4 -> { // 8*4 = 32 bits
            bitBuffer = (bitBuffer shl 8) + this.retrieveBits(i++)
            bitBuffer = (bitBuffer shl 8) + this.retrieveBits(i++)
            bitBuffer = (bitBuffer shl 8) + this.retrieveBits(i++)
            bitBuffer = (bitBuffer shl 8) + this.retrieveBits(i)
            out[index++] = base32Lookup[(bitBuffer shr 27 and 0x1fL).toInt()] // 32-1*5 = 27
            out[index++] = base32Lookup[(bitBuffer shr 22 and 0x1fL).toInt()] // 32-2*5 = 22
            out[index++] = base32Lookup[(bitBuffer shr 17 and 0x1fL).toInt()] // 32-3*5 = 17
            out[index++] = base32Lookup[(bitBuffer shr 12 and 0x1fL).toInt()] // 32-4*5 = 12
            out[index++] = base32Lookup[(bitBuffer shr  7 and 0x1fL).toInt()] // 32-5*5 = 7
            out[index++] = base32Lookup[(bitBuffer shr  2 and 0x1fL).toInt()] // 32-6*5 = 2
            out[index++] = base32Lookup[(bitBuffer shl  3 and 0x1fL).toInt()] // 5-2 = 3
            out[index]   = '='.toByte()
        }
    }

    return out.commonToUtf8String()
}

@Suppress("NOTHING_TO_INLINE")
private inline fun ByteArray.retrieveBits(index: Int): Long =
    this[index].toLong().let { bits ->
        return if (bits < 0) {
            bits + 256L
        } else {
            bits
        }
    }

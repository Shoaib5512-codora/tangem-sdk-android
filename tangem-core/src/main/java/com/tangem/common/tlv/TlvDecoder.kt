package com.tangem.common.tlv

import com.tangem.*
import com.tangem.commands.common.IssuerDataMode
import com.tangem.commands.common.card.CardStatus
import com.tangem.commands.common.card.EllipticCurve
import com.tangem.commands.common.card.masks.ProductMask
import com.tangem.commands.common.card.masks.SettingsMask
import com.tangem.commands.common.card.masks.SigningMethodMask
import com.tangem.commands.file.FileDataMode
import com.tangem.commands.file.FileSettings
import com.tangem.common.extensions.toDate
import com.tangem.common.extensions.toHexString
import com.tangem.common.extensions.toInt
import com.tangem.common.extensions.toUtf8
import java.util.*

/**
 * Maps value fields in [Tlv] from raw [ByteArray] to concrete classes
 * according to their [TlvTag] and corresponding [TlvValueType].
 *
 * @property tlvList List of TLVs, which values are to be converted to particular classes.
 */
class TlvDecoder(val tlvList: List<Tlv>) {

    init {
        Log.write(TypedMessage(MessageType.RECEIVE_TLV, tlvList.joinToString("\n")))
    }

    /**
     * Finds [Tlv] by its [TlvTag].
     * Returns null if [Tlv] is not found, otherwise converts its value to [T].
     *
     * @param tag [TlvTag] of a [Tlv] which value is to be returned.
     *
     * @return Value converted to a nullable type [T].
     */
    inline fun <reified T> decodeOptional(tag: TlvTag): T? =
            try {
                decode<T>(tag, false)
            } catch (exception: TangemSdkError.DecodingFailedMissingTag) {
                null
            }

    /**
     * Finds [Tlv] by its [TlvTag].
     * Throws [TaskError.MissingTag] if [Tlv] is not found,
     * otherwise converts [Tlv] value to [T].
     *
     * @param tag [TlvTag] of a [Tlv] which value is to be returned.
     *
     * @return [Tlv] value converted to a nullable type [T].
     *
     * @throws [TangemSdkError.DecodingFailedMissingTag] exception if no [Tlv] is found by the Tag.
     */
    inline fun <reified T> decode(tag: TlvTag, logError: Boolean = true): T {
        val tlvValue: ByteArray = tlvList.find { it.tag == tag }?.value
                ?: if (tag.valueType() == TlvValueType.BoolValue && T::class == Boolean::class) {
                    return false as T
                } else {
                    if (logError) {
                        Log.e(this::class.simpleName!!, "TLV $tag not found")
                    } else {
                        Log.i(this::class.simpleName!!, "TLV $tag not found, but it is not required")
                    }
                    throw TangemSdkError.DecodingFailedMissingTag("TLV $tag not found")
                }

        return when (tag.valueType()) {
            TlvValueType.HexString, TlvValueType.HexStringToHash -> {
                typeCheck<T, String>(tag)
                tlvValue.toHexString() as T
            }
            TlvValueType.Utf8String -> {
                typeCheck<T, String>(tag)
                tlvValue.toUtf8() as T
            }
            TlvValueType.Uint8, TlvValueType.Uint16, TlvValueType.Uint32 -> {
                typeCheck<T, Int>(tag)
                try {
                    tlvValue.toInt() as T
                } catch (exception: IllegalArgumentException) {
                    Log.e(this::class.simpleName!!, exception.message ?: "")
                    throw TangemSdkError.DecodingFailed(provideDecodingFailedMessage(tag))
                }
            }
            TlvValueType.BoolValue -> {
                typeCheck<T, Boolean>(tag)
                true as T
            }
            TlvValueType.ByteArray -> {
                typeCheck<T, ByteArray>(tag)
                tlvValue as T
            }
            TlvValueType.EllipticCurve -> {
                typeCheck<T, EllipticCurve>(tag)
                try {
                    EllipticCurve.byName(tlvValue.toUtf8()) as T
                } catch (exception: Exception) {
                    logException(tag, tlvValue.toUtf8(), exception)
                    throw TangemSdkError.DecodingFailed(provideDecodingFailedMessage(tag))
                }


            }
            TlvValueType.DateTime -> {
                typeCheck<T, Date>(tag)
                try {
                    tlvValue.toDate() as T
                } catch (exception: Exception) {
                    logException(tag, tlvValue.toHexString(), exception)
                    throw TangemSdkError.DecodingFailed(provideDecodingFailedMessage(tag))
                }
            }
            TlvValueType.ProductMask -> {
                typeCheck<T, ProductMask>(tag)
                ProductMask(tlvValue.toInt()) as T
            }
            TlvValueType.SettingsMask -> {
                typeCheck<T, SettingsMask>(tag)
                SettingsMask(tlvValue.toInt()) as T
            }
            TlvValueType.CardStatus -> {
                typeCheck<T, CardStatus>(tag)
                try {
                    CardStatus.byCode(tlvValue.toInt()) as T
                } catch (exception: Exception) {
                    logException(tag, tlvValue.toInt().toString(), exception)
                    throw TangemSdkError.DecodingFailed(provideDecodingFailedMessage(tag))
                }
            }
            TlvValueType.SigningMethod -> {
                typeCheck<T, SigningMethodMask>(tag)
                try {
                    SigningMethodMask(tlvValue.toInt()) as T
                } catch (exception: Exception) {
                    logException(tag, tlvValue.toInt().toString(), exception)
                    throw TangemSdkError.DecodingFailed(provideDecodingFailedMessage(tag))
                }
            }
            TlvValueType.IssuerDataMode -> {
                typeCheck<T, IssuerDataMode>(tag)
                try {
                    IssuerDataMode.byCode(tlvValue.toInt().toByte()) as T
                } catch (exception: Exception) {
                    logException(tag, tlvValue.toInt().toString(), exception)
                    throw TangemSdkError.DecodingFailed(provideDecodingFailedMessage(tag))
                }
            }
            TlvValueType.FileDataMode -> {
                typeCheck<T, FileDataMode>(tag)
                try {
                    FileDataMode.byRawValue(tlvValue.toInt()) as T
                } catch (exception: Exception) {
                    logException(tag, tlvValue.toInt().toString(), exception)
                    throw TangemSdkError.DecodingFailed(provideDecodingFailedMessage(tag))
                }
            }
            TlvValueType.FileSettings -> {
                typeCheck<T, FileSettings>(tag)
                try {
                    FileSettings.byRawValue(tlvValue.toInt()) as T
                } catch (exception: Exception) {
                    logException(tag, tlvValue.toInt().toString(), exception)
                    throw TangemSdkError.DecodingFailed(provideDecodingFailedMessage(tag))
                }
            }
        }
    }

    fun provideDecodingFailedMessage(tag: TlvTag): String =
            "Decoding failed. Failed to convert $tag to ${tag.valueType()}"

    fun logException(tag: TlvTag, value: String, exception: Exception) {
        Log.e(this::class.simpleName!!,
                "Unknown ${tag.name} with value of: $value, \n${exception.message}")
    }

    inline fun <reified T, reified ExpectedT> typeCheck(tag: TlvTag) {
        if (T::class != ExpectedT::class) {
            val error = TangemSdkError.DecodingFailedTypeMismatch(
                    "Mapping error. Type for tag: $tag must be ${tag.valueType()}. It is ${T::class}"
            )
            Log.e(this::class.simpleName!!, error.customMessage)
            throw error
        }
    }
}
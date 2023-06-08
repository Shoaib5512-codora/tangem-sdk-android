@file:OptIn(ExperimentalUnsignedTypes::class)

package com.tangem.crypto

import com.chiachat.kbls.bech32.KHex
import com.chiachat.kbls.bls.constants.BLS12381
import com.chiachat.kbls.bls.constants.Schemes
import com.chiachat.kbls.bls.ec.JacobianPoint
import com.chiachat.kbls.bls.keys.PrivateKey
import com.chiachat.kbls.bls.util.Hkdf
import com.chiachat.kbls.bls.util.OpSwuG2
import com.tangem.common.card.EllipticCurve
import com.tangem.common.core.TangemSdkError
import com.tangem.common.extensions.calculateSha256

@ExperimentalUnsignedTypes
internal object Bls {
    // / Version of keygen algorithm prior to number 4 (used in Chia)
    val SALT_PRE_V4: ByteArray = "BLS-SIG-KEYGEN-SALT-".encodeToByteArray()
    // / Actual version of keygen algorithm
    val SALT: ByteArray = SALT_PRE_V4.calculateSha256()

    // / l, Calculated as ceil((3 * ceil(log2(r))) / 16). where r is s the order of the BLS 12-381
    private const val COUNT = 48

    internal fun generatePublicKey(privateKeyArray: ByteArray): ByteArray {
        val privateKey = PrivateKey.fromBytes(bytes = privateKeyArray.toUByteArray())
        return privateKey.getG1().toBytes().toByteArray()
    }

    internal fun isPrivateKeyValid(privateKey: ByteArray): Boolean {
        return JacobianPoint.fromBytes(privateKey.toUByteArray(), isExtension = false).isValid()
    }

    internal fun makeMasterKey(seed: ByteArray, salt: ByteArray = SALT): ByteArray {
        val okm = Hkdf.extractExpand(
            COUNT,
            seed.toUByteArray() + 0.toUByte(),
            salt.toUByteArray(),
            listOf(0, COUNT).map { it.toUByte() }.toUByteArray(),
        )
        return PrivateKey(KHex(okm).bigInt.mod(BLS12381.defaultEc.n)).toBytes().toByteArray()
    }

    @Throws(TangemSdkError::class)
    internal fun prepareHashToSign(message: ByteArray, curve: EllipticCurve): ByteArray {
        val dst = when (curve) {
            EllipticCurve.Bls12381G2 -> Schemes.basicSchemeDst
            EllipticCurve.Bls12381G2Aug -> Schemes.augSchemeDst
            EllipticCurve.Bls12381G2Pop -> Schemes.popSchemePopDst
            else -> throw TangemSdkError.UnsupportedCurve()
        }
        return OpSwuG2.g2Map(message.toUByteArray(), dst).toBytes().toByteArray()
    }
}

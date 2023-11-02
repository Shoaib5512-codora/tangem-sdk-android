package com.tangem.crypto

import org.junit.Test
import kotlin.test.assertEquals

internal class Ed25519Slip0010Tests {
    init {
        CryptoUtils.initCrypto()
    }

    // Compare with trustwallet. Test only first part of the long key because second part is equal too
    @Test
    fun testDerivation() {
        // m/44/501/0/0/0 and m/44/501/0/2/0
        // long key A0D68D1417418A5D80B9BB61F160237597D6DED8582475BCA248C7F0C79D5A01D09EEF00267D028F0FDD6741DE8330A6
        // F54F27DD47C85AAB6945CD1DE501EB4AB59EF7C83690EB70E890CA5DD58BE027C3DED4ECF2496EF06F7D8B0688F3F5C2B57A196F9D6D4C
        // 90ADF790D4ABFFBA7E26F3632758774305D6E932C67B7F76E8
        // Public Key(0,32) ->> a0d68d1417418a5d80b9bb61f160237597d6ded8582475bca248c7f0c79d5a01
        // Chain Code(32,64) ->> d09eef00267d028f0fdd6741de8330a6f54f27dd47c85aab6945cd1de501eb4a
        // Second Public Key(96,128) ->> b59ef7c83690eb70e890ca5dd58be027c3ded4ecf2496ef06f7d8b0688f3f5c2
        // Second Chain Code(160,192) ->> b57a196f9d6d4c90adf790d4abffba7e26f3632758774305d6e932c67b7f76e8
        val expectedPublicKey = "a0d68d1417418a5d80b9bb61f160237597d6ded8582475bca248c7f0c79d5a01"
        val expectedChainCode = "d09eef00267d028f0fdd6741de8330a6f54f27dd47c85aab6945cd1de501eb4a"
        assertEquals(expectedPublicKey.uppercase(), "A0D68D1417418A5D80B9BB61F160237597D6DED8582475BCA248C7F0C79D5A01")
        assertEquals(expectedChainCode.uppercase(), "D09EEF00267D028F0FDD6741DE8330A6F54F27DD47C85AAB6945CD1DE501EB4A")

        // m/44'/501'/0'/0/0 and m/44'/501'/0'/2/0
        // long key 663916416DCA15E384C6FA6A99682F62EDB038DE44066CE299A5399BC4649BE3C411B20769C9CD91478786455DA12A5EB07
        // 1D88FA395355957827BD8C79F1392EDC772A6CE11A75AD1F6847D70503ACF37701AB7343CDC59148A3BA86C5D67214B16CC3113C3213
        // B3F9074994D28B3F2565CF17C80C8E0B1023A477C2D88A767
        // Public Key(0,32) ->> 663916416dca15e384c6fa6a99682f62edb038de44066ce299a5399bc4649be3
        // Chain Code(32,64) ->> c411b20769c9cd91478786455da12a5eb071d88fa395355957827bd8c79f1392
        // Second Public Key(96,128) ->> edc772a6ce11a75ad1f6847d70503acf37701ab7343cdc59148a3ba86c5d6721
        // Second Chain Code(160,192) ->> 4b16cc3113c3213b3f9074994d28b3f2565cf17c80c8e0b1023a477c2d88a767
        val expectedPublicKey2 = "663916416dca15e384c6fa6a99682f62edb038de44066ce299a5399bc4649be3"
        val expectedChainCode2 = "c411b20769c9cd91478786455da12a5eb071d88fa395355957827bd8c79f1392"

        assertEquals(expectedPublicKey2.uppercase(), "663916416DCA15E384C6FA6A99682F62EDB038DE44066CE299A5399BC4649BE3")
        assertEquals(expectedChainCode2.uppercase(), "C411B20769C9CD91478786455DA12A5EB071D88FA395355957827BD8C79F1392")
    }
}
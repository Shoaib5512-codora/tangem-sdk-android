package com.tangem.common.core

import com.tangem.common.CardFilter
import com.tangem.operations.attestation.AttestationTask

class Config(
    /**
     * Enables or disables Linked Terminal feature. Default is **true**
     * Notes:
     * App can optionally generate ECDSA key pair Terminal_PrivateKey / Terminal_PublicKey. And then submit
     * Terminal_PublicKey to the card in any SIGN command. Once SIGN is successfully executed by COS (Card Operation
     * System), including PIN2 verification and/or completion of security delay, the submitted Terminal_PublicKey key
     * is stored by COS. After that, the App instance is deemed trusted by COS and COS will allow skipping security
     * delay for subsequent SIGN operations thus improving convenience without sacrificing security.
     *
     * In order to skip security delay, App should use Terminal_PrivateKey to compute the signature of the data being
     * submitted to SIGN command for signing and transmit this signature in Terminal_Transaction_Signature parameter
     * in the same SIGN command. COS will verify the correctness of Terminal_Transaction_Signature using previously
     * stored Terminal_PublicKey and, if correct, will skip security delay for the current SIGN operation.
     * If null, TangemSdk will turn on this feature automatically according to iPhone model
     * COS version 2.30 and later.
     */
    var linkedTerminal: Boolean? = null,

    /**
     * If not null, it will be used to validate Issuer data and issuer extra data.
     * If null, issuerPublicKey from current card will be used
     */
    var issuerPublicKey: ByteArray? = null,

    var handleErrors: Boolean = true,

    var savePin1InStaticField: Boolean = true,

    var savePin2InStaticField: Boolean = true,

    var howToIsEnabled: Boolean = true,

    /**
     * Full CID will be displayed, if null
     */
    var cardIdDisplayedNumbersCount: Int? = null,

    /**
     * ScanTask or scanCard method in TangemSdk class will use this mode to attest the card
     */
    var attestationMode: AttestationTask.Mode = AttestationTask.Mode.Normal,

    /**
     * If true, BAP cards will pass online attestation. Use only for debugging purposes and if you understand what to do
     */
    var allowUntrustedCards: Boolean = false,

    var filter: CardFilter = CardFilter.default()
)
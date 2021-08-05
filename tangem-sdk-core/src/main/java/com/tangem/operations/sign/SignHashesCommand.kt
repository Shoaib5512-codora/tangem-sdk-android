package com.tangem.operations.sign

import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionRunnable
import com.tangem.common.core.CompletionCallback
import com.tangem.operations.PreflightReadMode

/**
 * Signs transaction hash using a wallet private key, stored on the card.
 * @property hashes Array of transaction hashes.
 * @property walletPublicKey: Public key of the wallet, using for sign.
 */
typealias SignHashesResponse = SignResponse

class SignHashesCommand(
    private val hashes: Array<ByteArray>,
    private val walletPublicKey: ByteArray
) : CardSessionRunnable<SignHashesResponse> {

    override fun preflightReadMode(): PreflightReadMode = PreflightReadMode.ReadWallet(walletPublicKey)

    override fun run(session: CardSession, callback: CompletionCallback<SignHashesResponse>) {
        SignCommand(hashes, walletPublicKey).run(session, callback)
    }
}
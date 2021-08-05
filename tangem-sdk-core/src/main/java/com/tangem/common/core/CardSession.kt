package com.tangem.common.core

import com.tangem.*
import com.tangem.common.CompletionResult
import com.tangem.common.UserCode
import com.tangem.common.UserCodeType
import com.tangem.common.apdu.CommandApdu
import com.tangem.common.apdu.ResponseApdu
import com.tangem.common.card.EncryptionMode
import com.tangem.common.core.*
import com.tangem.common.extensions.calculateSha256
import com.tangem.common.json.JSONRPCConverter
import com.tangem.common.json.JSONRPCException
import com.tangem.common.json.JSONRPCRequest
import com.tangem.common.json.toJSONRPCResponse
import com.tangem.common.nfc.CardReader
import com.tangem.crypto.EncryptionHelper
import com.tangem.crypto.pbkdf2Hash
import com.tangem.operations.*
import com.tangem.operations.read.ReadCommand
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Allows interaction with Tangem cards. Should be opened before sending commands.
 *
 * @property environment
 * @property reader  is an interface that is responsible for NFC connection and
 * transfer of data to and from the Tangem Card.
 * @property viewDelegate is an  interface that allows interaction with users and shows relevant UI.
 * @property cardId ID, Unique Tangem card ID number. If not null, the SDK will check that you the card
 * with which you tapped a phone has this [cardId] and SDK will return
 * the [TangemSdkError.WrongCardNumber] otherwise.
 * @property initialMessage A custom description that will be shown at the beginning of the NFC session.
 * If null, a default header and text body will be used.
 */
class CardSession(
    val viewDelegate: SessionViewDelegate,
    val environment: SessionEnvironment,
    private val reader: CardReader,
    private val jsonRpcConverter: JSONRPCConverter,
    cardId: String? = null,
    private var initialMessage: Message? = null
) {

    var cardId: String? = cardId
        private set
    var connectedTag: TagType? = null
        private set
    var state = CardSessionState.Inactive
        private set

    val scope = CoroutineScope(Dispatchers.IO) + CoroutineExceptionHandler { _, ex ->
        val sw = StringWriter()
        ex.printStackTrace(PrintWriter(sw))
        Log.error { sw.toString() }
    }

    private var preflightReadMode: PreflightReadMode = PreflightReadMode.FullCardRead

    fun setInitialMessage(message: Message?) {
        initialMessage = message
        viewDelegate.setMessage(message)
    }

    /**
     * This method starts a card session, performs preflight [ReadCommand],
     * invokes [CardSessionRunnable.run] and closes the session.
     * @param runnable [CardSessionRunnable] that will be performed in the session.
     * @param callback will be triggered with a [CompletionResult] of a session.
     */
    fun <T : CardSessionRunnable<R>, R : CommandResponse> startWithRunnable(runnable: T, callback: CompletionCallback<R>) {
        if (state != CardSessionState.Inactive) {
            callback(CompletionResult.Failure(TangemSdkError.Busy()))
            return
        }

        Log.session { "Start card session with runnable" }
        prepareSession(runnable) { prepareResult ->
            when (prepareResult) {
                is CompletionResult.Success -> {
                    start { session, error ->
                        if (error != null) {
                            callback(CompletionResult.Failure(error))
                            return@start
                        }
                        Log.session { "Start runnable" }
                        runnable.run(this) { result ->
                            Log.session { "Runnable completed" }
                            when (result) {
                                is CompletionResult.Success -> stop()
                                is CompletionResult.Failure -> stopWithError(result.error)
                            }
                            callback(result)
                        }
                    }
                }
                is CompletionResult.Failure -> callback(CompletionResult.Failure(prepareResult.error))
            }

        }
    }

    /**
     * Starts a card session and performs preflight [ReadCommand].
     * @param onSessionStarted: callback with the card session. Can contain [TangemSdkError] if something goes wrong.
     */
    fun start(onSessionStarted: SessionStartedCallback) {
        Log.session { "Start card session with delegate" }
        state = CardSessionState.Active
        viewDelegate.onSessionStarted(cardId, initialMessage, environment.config.howToIsEnabled)

        reader.scope = scope
        reader.startSession()

        scope.launch {
            reader.tag.asFlow()
                    .filterNotNull()
                    .take(1)
                    .collect { tagType ->
                        if (tagType == TagType.Nfc && preflightReadMode != PreflightReadMode.None) {
                            preflightCheck(onSessionStarted)
                        } else {
                            onSessionStarted(this@CardSession, null)
                        }
                    }
        }

        scope.launch {
            reader.tag.asFlow()
                    .collect {
                        if (it == null) {
                            viewDelegate.onTagLost()
                        } else {
                            viewDelegate.onTagConnected()
                        }
                    }
        }

        scope.launch {
            reader.tag.asFlow()
                    .onCompletion {
                        if (it is CancellationException && it.message == TangemSdkError.UserCancelled().customMessage) {
                            stopWithError(TangemSdkError.UserCancelled())
                            viewDelegate.dismiss()
                            onSessionStarted(this@CardSession, TangemSdkError.UserCancelled())
                        }
                    }
                    .collect {
                        if (it == null && connectedTag != null && state == CardSessionState.Active) {
                            environment.encryptionKey = null
                            connectedTag = null
                        } else if (it != null) {
                            connectedTag = it
                        }
                    }
        }
    }

    fun run(jsonRequest: String, callback: (String) -> Unit) {
        val request: JSONRPCRequest = try {
            JSONRPCRequest(jsonRequest)
        } catch (ex: JSONRPCException) {
            callback(ex.toJSONRPCResponse(null).toJson())
            return
        }
        try {
            val runnable = jsonRpcConverter.convert(request)
            runnable.run(this) {
                callback(it.toJSONRPCResponse(request.id).toJson())
            }
        } catch (ex: JSONRPCException) {
            callback(ex.toJSONRPCResponse(request.id).toJson())
        }
    }

    private fun <T : CardSessionRunnable<*>> prepareSession(runnable: T, callback: CompletionCallback<Unit>) {
        Log.session { "Prepare card session" }
        preflightReadMode = runnable.preflightReadMode()
        runnable.prepare(this, callback)
    }

    private fun preflightCheck(onSessionStarted: SessionStartedCallback) {
        Log.session { "Start preflight check" }
        val preflightTask = PreflightReadTask(preflightReadMode, cardId)
        preflightTask.run(this) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    onSessionStarted(this, null)
                }
                is CompletionResult.Failure -> {
                    val wrongType = when (result.error) {
                        is TangemSdkError.WrongCardType -> WrongValueType.CardType
                        is TangemSdkError.WrongCardNumber -> WrongValueType.CardId
                        else -> null
                    }
                    if (wrongType != null) {
                        viewDelegate.onWrongCard(wrongType)
                        scope.launch(scope.coroutineContext) {
                            delay(3500)
                            if (connectedTag == null) {
                                onSessionStarted(this@CardSession, result.error)
                                stopWithError(result.error)
                            } else {
                                viewDelegate.onTagConnected()
                                preflightCheck(onSessionStarted)
                            }
                        }
                    } else {
                        onSessionStarted(this, result.error)
                        stopWithError(result.error)
                    }

                }
            }
        }
    }

    /**
     * Stops the current session with the text message.
     * @param message If null, the default message will be shown.
     */
    fun stop(message: Message? = null) {
        stopSession()
        viewDelegate.onSessionStopped(message)
    }

    /**
     * Stops the current session on error.
     * @param error An error that will be shown.
     */
    fun stopWithError(error: TangemError) {
        stopSession()
        if (error !is TangemSdkError.UserCancelled) {
            Log.error { "Finishing with error: ${error.code}" }
            viewDelegate.onError(error)
        } else {
            Log.debug { "User cancelled NFC session" }
        }
    }

    private fun stopSession() {
        Log.session { "Stop session" }
        state = CardSessionState.Inactive
        preflightReadMode = PreflightReadMode.FullCardRead
//        environmentService.saveEnvironmentValues(environment, cardId)
        reader.stopSession()
        scope.cancel()
    }

    fun send(apdu: CommandApdu, callback: CompletionCallback<ResponseApdu>) {
        Log.session { "Send" }
        val subscription = reader.tag.openSubscription()
        scope.launch {
            subscription.consumeAsFlow()
                    .filterNotNull()
                    .map { establishEncryptionIfNeeded() }
                    .map { apdu.encrypt(environment.encryptionMode, environment.encryptionKey) }
                    .map { encryptedApdu -> reader.transceiveApdu(encryptedApdu) }
                    .map { responseApdu -> decrypt(responseApdu) }
                    .catch { if (it is TangemSdkError) callback(CompletionResult.Failure(it)) }
                    .collect { result ->
                        when (result) {
                            is CompletionResult.Success -> {
                                subscription.cancel()
                                callback(result)
                            }
                            is CompletionResult.Failure -> {
                                when (result.error) {
                                    is TangemSdkError.TagLost -> Log.session { "Tag lost. Waiting for tag..." }
                                    else -> {
                                        subscription.cancel()
                                        callback(result)
                                    }
                                }
                            }
                        }
                    }
        }
    }

    fun pause(error: TangemError? = null) {
        reader.pauseSession()
    }

    fun resume() {
        reader.resumeSession()
    }

    private suspend fun establishEncryptionIfNeeded(): CompletionResult<Boolean> {
        Log.session { "Try establish encryption" }
        if (environment.encryptionMode == EncryptionMode.None || environment.encryptionKey != null) {
            return CompletionResult.Success(true)
        }

        val encryptionHelper = EncryptionHelper.create(environment.encryptionMode)
                ?: return CompletionResult.Failure(TangemSdkError.CryptoUtilsError())

        val openSessionCommand = OpenSessionCommand(encryptionHelper.keyA)
        val apdu = openSessionCommand.serialize(environment)

        when (val response = reader.transceiveApdu(apdu)) {
            is CompletionResult.Success -> {
                val result = try {
                    openSessionCommand.deserialize(environment, response.data)
                } catch (error: TangemSdkError) {
                    return CompletionResult.Failure(error)
                }

                val uid = result.uid
                val protocolKey = environment.accessCode.value?.pbkdf2Hash(uid, 50)
                        ?: return CompletionResult.Failure(TangemSdkError.CryptoUtilsError())

                val secret = encryptionHelper.generateSecret(result.sessionKeyB)
                val sessionKey = (secret + protocolKey).calculateSha256()
                environment.encryptionKey = sessionKey

                return CompletionResult.Success(true)
            }
            is CompletionResult.Failure -> return CompletionResult.Failure(response.error)
        }
    }

    private fun decrypt(result: CompletionResult<ResponseApdu>): CompletionResult<ResponseApdu> {
        return when (result) {
            is CompletionResult.Success -> {
                try {
                    CompletionResult.Success(result.data.decrypt(environment.encryptionKey))
                } catch (error: TangemSdkError) {
                    CompletionResult.Failure(error)
                }
            }
            is CompletionResult.Failure -> result
        }
    }

    fun requestUserCodeIfNeeded(type: UserCodeType, isFirstAttempt: Boolean, callback: CompletionCallback<Unit>) {
        val userCode = when (type) {
            UserCodeType.AccessCode -> environment.accessCode.value
            UserCodeType.Passcode -> environment.passcode.value
        }
        if (userCode != null) {
            callback(CompletionResult.Success(Unit))
            return
        }

        Log.session { "Request user code of type: $type" }
        viewDelegate.requestUserCode(type, isFirstAttempt) { stringValue ->
            val code = UserCode(type, stringValue)
            when (type) {
                UserCodeType.AccessCode -> environment.accessCode = code
                UserCodeType.Passcode -> environment.passcode = code
            }
            callback(CompletionResult.Success(Unit))
        }
    }


    enum class CardSessionState {
        Inactive,
        Active
    }
}

typealias SessionStartedCallback = (session: CardSession, error: TangemError?) -> Unit

enum class TagType {
    Nfc,
    Slix
}

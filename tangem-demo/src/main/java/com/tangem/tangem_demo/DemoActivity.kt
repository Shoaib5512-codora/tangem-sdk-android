package com.tangem.tangem_demo

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.slider.Slider
import com.google.gson.Gson
import com.tangem.Config
import com.tangem.TangemSdk
import com.tangem.commands.*
import com.tangem.commands.common.ResponseConverter
import com.tangem.commands.common.card.Card
import com.tangem.commands.common.card.EllipticCurve
import com.tangem.commands.common.card.masks.SigningMethod
import com.tangem.commands.file.FileData
import com.tangem.commands.file.FileDataSignature
import com.tangem.commands.file.FileSettings
import com.tangem.commands.file.FileSettingsChange
import com.tangem.common.CompletionResult
import com.tangem.common.extensions.calculateSha256
import com.tangem.common.extensions.hexToBytes
import com.tangem.common.extensions.toByteArray
import com.tangem.common.files.FileHashHelper
import com.tangem.crypto.CryptoUtils
import com.tangem.crypto.sign
import com.tangem.tangem_sdk_new.extensions.init
import kotlinx.android.synthetic.main.file_data.*
import kotlinx.android.synthetic.main.issuer_data.*
import kotlinx.android.synthetic.main.issuer_ex_data.*
import kotlinx.android.synthetic.main.scan_card.*
import kotlinx.android.synthetic.main.set_pin.*
import kotlinx.android.synthetic.main.sign.*
import kotlinx.android.synthetic.main.user_data.*
import kotlinx.android.synthetic.main.wallet.*

class DemoActivity : AppCompatActivity() {

    private val gson: Gson = ResponseConverter().gson
    private val mainThread = Handler(Looper.getMainLooper())

    private lateinit var sdk: TangemSdk
    private var card: Card? = null

    private var walletIndex: WalletIndex? = null

    private var bshDlg: BottomSheetDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_demo)

        sdk = TangemSdk.init(this, Config())
        scanCard()
        sign()
        wallet()
        issuerData()
        issuerExData()
        userData()
        setPin()
        setupFileDataButtons()
    }

    private fun scanCard() {
        btnScanCard.setOnClickListener { sdk.scanCard(walletIndex) { handleResult(it) } }
    }

    private fun sign() {
        btnSign.setOnClickListener {
            val listOfData = MutableList(10) { Utils.randomString(20) }
            val listOfHashes = listOfData.map { it.toByteArray() }
            sdk.sign(listOfHashes.toTypedArray(), walletIndex, card?.cardId) { handleResult(it) }
        }
    }

    private fun wallet() {
        val walletData = WalletData("BCH")
        val walletConfig = WalletConfig(
            true,
            false,
            EllipticCurve.Secp256k1,
            SigningMethod.SignHash,
            walletData
        )
        btnCreateWallet.setOnClickListener {
            val index = (walletIndex as? WalletIndex.Index)?.index ?: 0
            sdk.createWallet(walletConfig, index, card?.cardId) { handleResult(it) }
        }
        btnPurgeWallet.setOnClickListener { sdk.purgeWallet(walletIndex, card?.cardId) { handleResult(it) } }
    }

    private fun issuerData() {
        btnReadIssuerData.setOnClickListener { sdk.readIssuerData(card?.cardId) { handleResult(it) } }

        btnWriteIssuerData.setOnClickListener {
            val cardId = card?.cardId
            if (cardId == null) {
                showToast("CardId required. Scan your card before proceeding")
                return@setOnClickListener
            }

            val issuerData = Utils.randomString(Utils.randomInt(15, 30)).toByteArray()
            val counter = 1
            val issuerPrivateKey = Utils.issuer().dataKeyPair.privateKey
            val signedIssuerData = (cardId.hexToBytes() + issuerData + counter.toByteArray(4)).sign(issuerPrivateKey)

            sdk.writeIssuerData(cardId, issuerData, signedIssuerData, counter) { handleResult(it) }
        }
    }

    private fun issuerExData() {
        btnReadIssuerExData.setOnClickListener { sdk.readIssuerExtraData(card?.cardId) { handleResult(it) } }

        btnWriteIssuerExData.setOnClickListener {
            val cardId = card?.cardId
            if (cardId == null) {
                showToast("CardId required. Scan your card before proceeding")
                return@setOnClickListener
            }

            val counter = 1
            val issuerData = CryptoUtils.generateRandomBytes(WriteIssuerExtraDataCommand.SINGLE_WRITE_SIZE * 5)
            val signatures = FileHashHelper.prepareHashes(
                cardId, issuerData, counter, Utils.issuer().dataKeyPair.privateKey
            )

            sdk.writeIssuerExtraData(
                cardId, issuerData,
                signatures.startingSignature!!, signatures.finalizingSignature!!,
                counter
            ) { handleResult(it) }
        }

    }

    private fun userData() {
        btnReadUserData.setOnClickListener { sdk.verify(card?.cardId) { handleResult(it) } }

        btnWriteUserData.setOnClickListener {
            val userData = "Some of user data ${Utils.randomString(20)}".toByteArray()
            val counter = 1
            sdk.writeUserData(card?.cardId, userData, counter) { handleResult(it) }
        }

        btnWriteUserProtectedData.setOnClickListener {
            val userProtectedData = "Some of user protected data ${Utils.randomString(20)}".toByteArray()
            val counter = 1
            sdk.writeUserProtectedData(card?.cardId, userProtectedData, counter) { handleResult(it) }
        }
    }

    private fun setPin() {
        btnSetPin1.setOnClickListener {
            sdk.startSessionWithRunnable(
                SetPinCommand(PinType.Pin1, null, sdk.config.defaultPin2.calculateSha256()), card?.cardId) { handleResult(it) }
        }
        btnSetPin2.setOnClickListener {
            sdk.startSessionWithRunnable(
                SetPinCommand(PinType.Pin2, sdk.config.defaultPin1.calculateSha256(), null), card?.cardId) { handleResult(it) }
        }
    }

    private fun setupFileDataButtons() {
        btnReadAllFiles.setOnClickListener {
            sdk.readFiles(true) { handleResult(it) }
        }
        btnReadPublicFiles.setOnClickListener {
            sdk.readFiles { handleResult(it) }
        }
        btnWriteSignedFile.setOnClickListener {
            val cardId = card?.cardId
            if (cardId == null) {
                showToast("CardId required. Scan your card before proceeding")
                return@setOnClickListener
            }
            val file = prepareSignedData(cardId)
            sdk.writeFiles(listOf(file)) { handleResult(it) }
        }
        btnWriteFilesWithPasscode.setOnClickListener {
            val issuerData = CryptoUtils.generateRandomBytes(WriteIssuerExtraDataCommand.SINGLE_WRITE_SIZE * 5)
            val files = listOf(FileData.DataProtectedByPasscode(issuerData), FileData.DataProtectedByPasscode(issuerData))
            sdk.writeFiles(files) { handleResult(it) }
        }
        btnDeleteAll.setOnClickListener {
            sdk.deleteFiles { handleResult(it) }
        }
        btnDeleteFirst.setOnClickListener {
            sdk.deleteFiles(listOf(0)) { handleResult(it) }
        }
        btnMakeFilePublic.setOnClickListener {
            val change = FileSettingsChange(0, FileSettings.Public)
            sdk.changeFilesSettings(listOf(change)) { handleResult(it) }
        }
        btnMakeFilePrivate.setOnClickListener {
            val change = FileSettingsChange(0, FileSettings.Private)
            sdk.changeFilesSettings(listOf(change)) { handleResult(it) }
        }
    }

    private fun handleResult(completionResult: CompletionResult<*>) {
        when (completionResult) {
            is CompletionResult.Success -> {
                if (completionResult.data is Card) {
                    card = completionResult.data as? Card

                    if (walletIndex == null) {
                        val touchListener = object : Slider.OnSliderTouchListener {
                            override fun onStartTrackingTouch(slider: Slider) {
                            }

                            override fun onStopTrackingTouch(slider: Slider) {
                                val value = slider.value.toInt()
                                walletIndex = WalletIndex.Index(value)
                                tvWalletIndex.text = "$value"
                            }
                        }
                        sliderWallet.post {
                            if (card?.walletsCount == null) {
                                walletIndexesContainer.visibility = View.GONE
                                sliderWallet.removeOnSliderTouchListener(touchListener)
                            } else {
                                val walletsCount = card?.walletsCount?.toFloat() ?: 1f
                                if (walletsCount > 1) {
                                    walletIndexesContainer.visibility = View.VISIBLE
                                    sliderWallet.stepSize = 1f
                                    sliderWallet.valueFrom = 0f
                                    sliderWallet.valueTo = walletsCount - 1f
                                    sliderWallet.value = sliderWallet.valueFrom
                                    sliderWallet.addOnSliderTouchListener(touchListener)
                                    tvWalletIndex.text = "0"
                                }

                            }
                        }
                    }
                }

                val json = gson.toJson(completionResult.data)
                mainThread.post { showDialog(json) }
            }
            is CompletionResult.Failure -> {
                mainThread.post { showToast(completionResult.error.customMessage) }
            }
        }
    }

    private fun showDialog(message: String) {
        val dlg = bshDlg ?: BottomSheetDialog(this)
        if (dlg.isShowing) dlg.hide()

        dlg.setContentView(R.layout.bottom_sheet_response_layout)
        val tv = dlg.findViewById<TextView>(R.id.tvResponse) ?: return

        tv.text = message
        dlg.show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun prepareSignedData(cardId: String): FileData.DataProtectedBySignature {
        val counter = 1
        val issuerData = CryptoUtils.generateRandomBytes(WriteIssuerExtraDataCommand.SINGLE_WRITE_SIZE * 5)
        val signatures = FileHashHelper.prepareHashes(
            cardId, issuerData, counter, Utils.issuer().dataKeyPair.privateKey
        )
        return FileData.DataProtectedBySignature(
            issuerData, counter,
            FileDataSignature(signatures.startingSignature!!, signatures.finalizingSignature!!),
            Utils.issuer().dataKeyPair.publicKey
        )
    }
}
package com.gohj99.wearnfc

import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gohj99.wearnfc.sync.PhoneCommunication
import com.gohj99.wearnfc.ui.MobileApp
import com.gohj99.wearnfc.ui.theme.WearNFCTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class MainActivity : ComponentActivity() {
    private lateinit var model: MobileViewModel
    private val nfcAdapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[MobileViewModel::class.java]
        enableEdgeToEdge()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {
                    combine(model.add, PhoneCommunication.state) { draft, connection ->
                        (connection.connected && connection.watch?.id == draft.watchId &&
                            (draft.stage == AddStage.Scanning || draft.stage == AddStage.Reading)) to draft.scanAttempt
                    }.distinctUntilChanged().collect { (readingEnabled, _) ->
                        if (readingEnabled) {
                            val adapter = nfcAdapter
                            when {
                                adapter == null -> model.scanError("这台手机不支持 NFC")
                                !adapter.isEnabled -> model.scanError("请先开启手机 NFC，再返回重试")
                                else -> runCatching {
                                    adapter.enableReaderMode(this@MainActivity,
                                        { tag -> runOnUiThread { model.readTag(tag) } },
                                        NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null)
                                }.onFailure { model.scanError("无法启动 NFC 读卡，请重试") }
                            }
                        } else {
                            runCatching { nfcAdapter?.disableReaderMode(this@MainActivity) }
                        }
                    }
                } finally {
                    runCatching { nfcAdapter?.disableReaderMode(this@MainActivity) }
                }
            }
        }
        setContent { WearNFCTheme { MobileApp(model) } }
    }

    override fun onStart() {
        super.onStart()
        PhoneCommunication.start(applicationContext)
    }

    override fun onStop() {
        PhoneCommunication.stop()
        super.onStop()
    }
}

package com.gohj99.wearnfc

import android.content.Intent
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
        // 旋转屏幕时由 ViewModel 保留请求和草稿，不重复消费启动入口。
        if (savedInstanceState == null) handleLaunchIntent(intent)
        enableEdgeToEdge()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {
                    combine(model.add, PhoneCommunication.state) { draft, connection ->
                        (connection.connected &&
                            (draft.watchId.isBlank() || connection.watch?.id == draft.watchId) &&
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(intent: Intent) {
        val uri = intent.data
        val isAppUri = intent.action == Intent.ACTION_VIEW &&
            uri?.scheme == "android-app" && uri.host == packageName
        // 兼容系统直接交付 INSERT，以及伴侣应用原样转发 VIEW + android-app URI。
        val action = if (isAppUri) {
            runCatching { Intent.parseUri(uri.toString(), Intent.URI_ANDROID_APP_SCHEME).action }.getOrNull()
        } else intent.action
        if (action == Intent.ACTION_INSERT || (isAppUri && action == Intent.ACTION_MAIN)) {
            model.requestAddFromWatch()
        }
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

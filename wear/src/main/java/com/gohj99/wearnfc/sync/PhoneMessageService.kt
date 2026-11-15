package com.gohj99.wearnfc.sync

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class PhoneMessageService : WearableListenerService() {
    // WearableListenerService 在自己的工作线程派发；等待处理结束，避免服务先销毁。
    override fun onMessageReceived(event: MessageEvent) {
        runBlocking(Dispatchers.IO) { WatchCommunication.receive(applicationContext, event) }
    }
}

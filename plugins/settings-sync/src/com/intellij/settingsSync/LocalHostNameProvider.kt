package com.intellij.settingsSync

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.net.InetAddress
import kotlin.coroutines.EmptyCoroutineContext

@Service(Service.Level.APP)
internal class LocalHostNameProvider: Disposable {
  private val cs = CoroutineScope(EmptyCoroutineContext)
  private val hostName = MutableStateFlow<String?>(null)

  init {
    cs.launch(Dispatchers.IO) {
      hostName.value = try {
        InetAddress.getLocalHost().hostName
      }
      catch (e: Exception) {
        LOG.error(e)
        defaultHostName
      }
    }
  }

  fun getHostName(): String {
    return hostName.value ?: defaultHostName
  }

  override fun dispose() {
    cs.cancel()
  }

  companion object {
    private val LOG = Logger.getInstance(LocalHostNameProvider::class.java)
    private val defaultHostName get() = System.getenv("HOSTNAME") ?: System.getenv("COMPUTERNAME") ?: "unknown"

    fun initialize() {
      service<LocalHostNameProvider>()
    }
  }
}
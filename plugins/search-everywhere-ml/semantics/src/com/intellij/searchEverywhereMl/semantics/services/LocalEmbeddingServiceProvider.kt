package com.intellij.searchEverywhereMl.semantics.services

import ai.grazie.emb.local.LocalEmbeddingService
import ai.grazie.emb.local.LocalEmbeddingServiceLoader
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.ref.SoftReference

/**
 * Thread-safe wrapper around [LocalEmbeddingServiceLoader] that caches [LocalEmbeddingService]
 * so that when the available heap memory is low, the neural network model is unloaded.
 */
@Service
class LocalEmbeddingServiceProvider {
  // Allow garbage collector to free memory if the available heap size is low
  private var localServiceRef: SoftReference<LocalEmbeddingService>? = null
  private val mutex = Mutex()

  suspend fun getService(): LocalEmbeddingService? {
    return mutex.withLock {
      var service = localServiceRef?.get()
      if (service == null) {
        val artifactsManager = LocalArtifactsManager.getInstance()
        if (!artifactsManager.checkArtifactsPresent()) return null
        service = LocalEmbeddingServiceLoader().load(artifactsManager.getCustomRootDataLoader())
        localServiceRef = SoftReference(service)
      }
      service
    }
  }

  companion object {
    fun getInstance() = service<LocalEmbeddingServiceProvider>()
  }
}
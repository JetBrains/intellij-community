package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.searchEverywhereMl.semantics.models.LocalEmbeddingService
import com.intellij.searchEverywhereMl.semantics.models.LocalEmbeddingServiceLoader
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

  private suspend fun getService(): LocalEmbeddingService? {
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

  fun getServiceBlocking(): LocalEmbeddingService? = runBlockingCancellable { getService() }

  companion object {
    fun getInstance() = service<LocalEmbeddingServiceProvider>()
  }
}
@file:JvmName("TextMateBundlesLoader")

package org.jetbrains.plugins.textmate

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.util.IntellijInternalApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import java.util.function.Consumer

@IntellijInternalApi
@JvmOverloads
internal fun registerBundlesInParallel(scope: CoroutineScope,
                                       bundlesToLoad: List<TextMateBundleToLoad>,
                                       registrar: (TextMateBundleToLoad) -> Boolean,
                                       registrationFailed: Consumer<TextMateBundleToLoad>? = null) {
  fun handleError(bundleToLoad: TextMateBundleToLoad, t: Throwable? = null) {
    if (registrationFailed == null || ApplicationManager.getApplication().isHeadlessEnvironment) {
      TextMateService.LOG.error("Cannot load builtin textmate bundle", t, bundleToLoad.toString())
    }
    else {
      scope.launch(Dispatchers.EDT) {
        registrationFailed.accept(bundleToLoad)
      }
    }
  }

  ProgressManager.checkCanceled()

  val initializationJob = scope.launch(Dispatchers.IO) {
    bundlesToLoad.map { bundleToLoad ->
      launch {
        runCatching {
          registrar(bundleToLoad)
        }.onFailure { t ->
          handleError(bundleToLoad, t)
        }.onSuccess { registered ->
          if (!registered) {
            handleError(bundleToLoad)
          }
        }
      }
    }
  }
  ProgressIndicatorUtils.awaitWithCheckCanceled(initializationJob.asCompletableFuture())
}

data class TextMateBundleToLoad(val name: String, val path: String)
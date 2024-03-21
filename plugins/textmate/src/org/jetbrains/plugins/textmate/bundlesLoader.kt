@file:JvmName("TextMateBundlesLoader")

package org.jetbrains.plugins.textmate

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.util.IntellijInternalApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException
import java.util.function.Consumer

@IntellijInternalApi
@JvmOverloads
internal fun registerBundlesInParallel(scope: CoroutineScope,
                                       bundlesToLoad: List<TextMateBundleToLoad>,
                                       registrar: suspend (TextMateBundleToLoad) -> Boolean,
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

  val initializationJob = scope.launch {
    for (bundleToLoad in bundlesToLoad) {
      launch {
        val registered = try {
          registrar(bundleToLoad)
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Throwable) {
          handleError(bundleToLoad, e)
          null
        }

        if (registered != null && !registered) {
          handleError(bundleToLoad)
        }
      }
    }
  }

  ProgressIndicatorUtils.awaitWithCheckCanceled(initializationJob.asCompletableFuture())
}

data class TextMateBundleToLoad(val name: String, val path: String)
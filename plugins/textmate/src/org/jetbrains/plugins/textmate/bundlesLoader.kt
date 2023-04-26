@file:JvmName("TextMateBundlesLoader")

package org.jetbrains.plugins.textmate

import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import java.nio.file.Path
import java.util.function.Consumer

@JvmOverloads
internal fun registerBundlesInParallel(scope: CoroutineScope,
                                       bundlePaths: List<Path>,
                                       registrar: java.util.function.Function<Path, Boolean>,
                                       registrationFailed: Consumer<Path>? = null) {
  val initializationJob = scope.launch(Dispatchers.IO) {
    coroutineScope {
      bundlePaths.map { bundlePath ->
        launch {
          if (!registrar.apply(bundlePath)) {
            if (registrationFailed != null) {
              withContext(Dispatchers.EDT) {
                registrationFailed.accept(bundlePath)
              }
            }
            else {
              TextMateService.LOG.error("Cannot load builtin textmate bundle", bundlePath.toString())
            }
          }
        }
      }
    }
  }
  ProgressIndicatorUtils.awaitWithCheckCanceled(initializationJob.asCompletableFuture())
}
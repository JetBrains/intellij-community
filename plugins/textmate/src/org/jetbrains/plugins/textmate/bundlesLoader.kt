@file:JvmName("TextMateBundlesLoader")

package org.jetbrains.plugins.textmate

import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.util.io.blockingDispatcher
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import java.nio.file.Path
import java.util.function.Consumer

@OptIn(DelicateCoroutinesApi::class)
@IntellijInternalApi
@JvmOverloads
internal fun registerBundlesInParallel(scope: CoroutineScope,
                                       bundlePaths: List<Path>,
                                       registrar: (Path) -> Boolean,
                                       registrationFailed: Consumer<Path>? = null) {
  val initializationJob = scope.launch(blockingDispatcher) {
    bundlePaths.map { bundlePath ->
      launch {
        if (!registrar(bundlePath)) {
          if (registrationFailed == null) {
            TextMateService.LOG.error("Cannot load builtin textmate bundle", bundlePath.toString())
          }
          else {
            withContext(Dispatchers.EDT) {
              registrationFailed.accept(bundlePath)
            }
          }
        }
      }
    }
  }
  ProgressIndicatorUtils.awaitWithCheckCanceled(initializationJob.asCompletableFuture())
}
@file:JvmName("TextMateBundlesLoader")

package org.jetbrains.plugins.textmate

import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.function.Consumer

@JvmOverloads
internal fun registerBundlesInParallel(scope: CoroutineScope,
                                       bundlePaths: List<Path>,
                                       registrar: (Path) -> Boolean,
                                       registrationFailed: Consumer<Path>? = null) {
  val initializationJob = scope.launch {
    bundlePaths.map { bundlePath ->
      launch(Dispatchers.IO) {
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
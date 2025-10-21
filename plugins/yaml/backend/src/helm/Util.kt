package org.jetbrains.yaml.helm

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.originalFileOrSelf
import org.jetbrains.annotations.ApiStatus

const val TEMPLATES_DIR_NAME: String = "templates"

@ApiStatus.Internal
fun hasYamlExtension(file: VirtualFile): Boolean = file.extension == "yaml" || file.extension == "yml"

@ApiStatus.Internal
fun hasJsonExtension(file: VirtualFile): Boolean = file.extension == "json"

@ApiStatus.Internal
fun isHelmTemplateFile(file: VirtualFile): Boolean {
  var chartDirCandidate = VfsUtilCore.findContainingDirectory(file.originalFileOrSelf(), TEMPLATES_DIR_NAME)?.parent
  while (chartDirCandidate != null) {
    ProgressManager.checkCanceled()

    if (!chartDirCandidate.isValid) return false

    // Simple Helm chart
    if (chartDirCandidate.findChild("Chart.yaml") != null) return true

    // Werf Helm chart
    if (chartDirCandidate.name == ".helm") return true

    chartDirCandidate = VfsUtilCore.findContainingDirectory(chartDirCandidate.originalFileOrSelf(), TEMPLATES_DIR_NAME)?.parent
  }
  return false
}

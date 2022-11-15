package org.intellij.plugins.markdown.model.psi

import com.intellij.model.Pointer
import com.intellij.navigation.NavigationRequest
import com.intellij.navigation.NavigationService
import com.intellij.navigation.NavigationTarget
import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.MarkdownBundle
import org.jetbrains.annotations.NonNls

internal open class MarkdownSourceNavigationTarget(
  private val file: VirtualFile,
  private val offset: Int,
  private val text: @NonNls String
): NavigationTarget {
  override fun createPointer(): Pointer<out NavigationTarget> {
    return Pointer.hardPointer(this)
  }

  override fun presentation(): TargetPresentation {
    val builder = TargetPresentation.builder(MarkdownBundle.message("markdown.source.navigation.target.text", text))
    return builder.presentation()
  }

  override fun navigationRequest(): NavigationRequest? {
    if (!file.isValid) {
      return null
    }
    return NavigationService.instance().sourceNavigationRequest(file, offset)
  }
}

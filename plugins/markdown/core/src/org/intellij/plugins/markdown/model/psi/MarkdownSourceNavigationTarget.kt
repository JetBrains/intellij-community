package org.intellij.plugins.markdown.model.psi

import com.intellij.model.Pointer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.NavigationTarget

internal abstract class MarkdownSourceNavigationTarget(
  private val file: VirtualFile,
  private val offset: Int,
): NavigationTarget {
  override fun createPointer(): Pointer<out NavigationTarget> {
    return Pointer.hardPointer(this)
  }

  override fun navigationRequest(): NavigationRequest? {
    if (!file.isValid) {
      return null
    }
    return NavigationRequest.sourceNavigationRequest(file, offset)
  }
}

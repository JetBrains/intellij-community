package org.intellij.plugins.markdown.model.psi

import com.intellij.model.Pointer
import com.intellij.navigation.NavigationRequest
import com.intellij.navigation.NavigationService
import com.intellij.navigation.NavigationTarget
import com.intellij.openapi.vfs.VirtualFile

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
    return NavigationService.instance().sourceNavigationRequest(file, offset)
  }
}

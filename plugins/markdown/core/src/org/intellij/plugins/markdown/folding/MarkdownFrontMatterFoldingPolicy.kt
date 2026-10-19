package org.intellij.plugins.markdown.folding

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

/** Lets a file kind opt out of the front matter fold region. A region exists only when every policy allows it. */
@ApiStatus.Experimental
interface MarkdownFrontMatterFoldingPolicy {
  /**
   * Returns `false` to remove the front matter fold region from [file].
   *
   * The folding builder calls this function in a read action, on any thread.
   * The call can also occur in dumb mode, so do not use indexes.
   * Keep the call fast, because the folding builder calls it on each folding update.
   */
  fun isFoldable(file: PsiFile): Boolean

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<MarkdownFrontMatterFoldingPolicy> =
      ExtensionPointName.create("org.intellij.markdown.frontMatterFoldingPolicy")
  }
}

package org.intellij.plugins.markdown.lang.parser

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.FileViewProvider
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.jetbrains.annotations.ApiStatus

/**
 * Extension point to override default flavour used by Markdown plugin.
 *
 * Use [MarkdownDefaultFlavour] and [MarkdownDefaultMarkerProcessor] to implement your custom flavour.
 */
@ApiStatus.Experimental
interface MarkdownFlavourProvider {
  /**
   * Will be called during creation of the [org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile].
   * The first non-null result will be selected as a flavour for newly created file.
   * If none of the providers were able to provide a custom flavour, [MarkdownDefaultFlavour] will be used.
   *
   * @return [MarkdownFlavourDescriptor] or `null` to indicate that another provider should be used.
   */
  fun provideFlavour(viewProvider: FileViewProvider): MarkdownFlavourDescriptor?

  companion object {
    private const val extensionPointName = "org.intellij.markdown.flavourProvider"

    internal val extensionPoint = ExtensionPointName<MarkdownFlavourProvider>(extensionPointName)

    @JvmStatic
    fun findFlavour(viewProvider: FileViewProvider): MarkdownFlavourDescriptor {
      val providers = extensionPoint.extensionList
      val flavour = providers.firstNotNullOfOrNull { it.provideFlavour(viewProvider) }
      return when (flavour) {
        null -> obtainDefaultMarkdownFlavour()
        else -> flavour
      }
    }
  }
}

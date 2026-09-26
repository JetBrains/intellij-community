package org.intellij.plugins.markdown.lang.parser

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
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
   * Will be called during creation of the [org.intellij.plugins.markdown.lang.parser.MarkdownParserDefinition]
   * and [org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile] in particular.
   * The first non-null result will be selected as a flavour for newly created file.
   * If none of the providers were able to provide a custom flavour, [MarkdownDefaultFlavour] will be used.
   *
   * @return [MarkdownFlavourDescriptor] or `null` to indicate that another provider should be used.
   */
  fun provideFlavour(viewProvider: FileViewProvider): MarkdownFlavourDescriptor? = provideFlavour(viewProvider.manager.project)

  /**
   * Will be called during creation of the [org.intellij.plugins.markdown.lang.parser.MarkdownParserDefinition]
   * and [org.intellij.plugins.markdown.lang.lexer.MarkdownToplevelLexer] / [org.intellij.plugins.markdown.lang.parser.MarkdownParserAdapter]
   * in particular.
   * The first non-null result will be selected as a flavour.
   * If none of the providers were able to provide a custom flavour, [MarkdownDefaultFlavour] will be used.
   *
   * @return [MarkdownFlavourDescriptor] or `null` to indicate that another provider should be used.
   */
  fun provideFlavour(project: Project): MarkdownFlavourDescriptor? = null // To prevent API breakage

  companion object {
    private const val extensionPointName = "org.intellij.markdown.flavourProvider"

    @ApiStatus.Internal
    val extensionPoint = ExtensionPointName<MarkdownFlavourProvider>(extensionPointName)

    @JvmStatic
    fun findFlavour(viewProvider: FileViewProvider): MarkdownFlavourDescriptor {
      val providers = extensionPoint.extensionList
      return when (val flavour = providers.firstNotNullOfOrNull { it.provideFlavour(viewProvider) }) {
        null -> obtainDefaultMarkdownFlavour()
        else -> flavour
      }
    }

    @JvmStatic
    fun findFlavour(project: Project): MarkdownFlavourDescriptor {
      val providers = extensionPoint.extensionList
      return when (val flavour = providers.firstNotNullOfOrNull { it.provideFlavour(project) }) {
        null -> obtainDefaultMarkdownFlavour()
        else -> flavour
      }
    }
  }
}

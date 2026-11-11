@file:JvmName("MarkdownFlavourUtil")
package org.intellij.plugins.markdown.lang.parser

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiFile
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.jetbrains.annotations.ApiStatus

private val LOG = fileLogger()

@ApiStatus.Internal
fun createMarkdownFile(viewProvider: FileViewProvider): MarkdownFile {
  val flavour = MarkdownFlavourProvider.findFlavour(viewProvider)
  return MarkdownFile(viewProvider, flavour)
}

@ApiStatus.Internal
fun obtainFlavour(file: PsiFile): MarkdownFlavourDescriptor {
  return obtainFlavourOrNull(file) ?: obtainDefaultMarkdownFlavour().also {
    LOG.error("Markdown flavour was not set for $file. Using default flavour as a fallback: $it")
  }
}

@ApiStatus.Experimental
fun obtainFlavourOrNull(file: PsiFile): MarkdownFlavourDescriptor? {
  return (file as? MarkdownFile)?.flavour ?: file.getUserData(MarkdownParserManager.FLAVOUR_DESCRIPTION)
}

internal fun obtainDefaultMarkdownFlavour(): MarkdownFlavourDescriptor {
  return MarkdownParserManager.FLAVOUR
}

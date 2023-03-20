@file:JvmName("MarkdownFlavourUtil")
package org.intellij.plugins.markdown.lang.parser

import com.intellij.psi.FileViewProvider
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

internal fun createMarkdownFile(viewProvider: FileViewProvider): MarkdownFile {
  val flavour = MarkdownFlavourProvider.findFlavour(viewProvider)
  return MarkdownFile(viewProvider, flavour)
}

internal fun obtainDefaultMarkdownFlavour(): MarkdownFlavourDescriptor {
  return MarkdownParserManager.FLAVOUR
}

package com.intellij.mcpserver.util

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SymbolInfo(
  @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
  val name: String? = null,
  val declarationText: String,
  @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
  val declarationFile: String? = null,
  @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
  val declarationLine: Int? = null,
  @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
  val language: String? = null,
)

/**
 * Returns symbol info for the given [psiElement].
 * The text will be taken as in the following way:
 * - the start line will be the line where the element is started,
 * - the end line will be the line where the name of the element ends if the name element is present,
 * - extra lines will be added after the name if [extraLines] > 0
 * This logic allows grabbing the declaration with the name and the doc comment if it's present
 */
@RequiresReadLock
fun getElementSymbolInfo(psiElement: PsiElement, extraLines: Int = 0): SymbolInfo? {
  val navigationElement = psiElement.navigationElement ?: return null
  val document = navigationElement.containingFile?.fileDocument ?: return null
  val elementTextRange = navigationElement.textRange ?: return null
  val nameIdentifierTextRange = (navigationElement as? PsiNameIdentifierOwner)?.nameIdentifier?.textRange
  val nameStartLine = nameIdentifierTextRange?.let { document.getLineNumber(it.startOffset) }
  // choose start of element or name identifier, e.g. to take the whole doc comment
  val startOffset = min(elementTextRange.startOffset, nameIdentifierTextRange?.startOffset ?: elementTextRange.startOffset)
  val startLine = document.getLineNumber(startOffset)
  val endOffset = max(elementTextRange.startOffset, nameIdentifierTextRange?.endOffset ?: elementTextRange.startOffset)
  val endLine = document.getLineNumber(endOffset)

  val surroundingsTextRange = document.getWholeLinesTextRange(startLine..(endLine + extraLines))
  val projectDirectory = navigationElement.project.projectDirectory
  val declarationFilePath = navigationElement.containingFile.virtualFile?. let { projectDirectory.relativizeIfPossible(it) }
  return SymbolInfo(
    name = (psiElement as? PsiNamedElement)?.name,
    declarationText = "<…>\n${document.getText(surroundingsTextRange)}\n<…>",
    declarationFile = declarationFilePath,
    declarationLine = (nameStartLine ?: startLine) + 1,
    language = navigationElement.language.displayName,
  )
}
package org.intellij.plugins.markdown.util

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.TreeUtil
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.Consumer
import com.intellij.util.NullableConsumer
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeaderImpl

internal object MarkdownPsiUtil {
  /** Check if node is on a top-level -- meaning its parent is root of file   */
  fun isTopLevel(node: ASTNode) = node.treeParent.hasType(MarkdownTokenTypeSets.MARKDOWN_FILE)

  object WhiteSpaces {
    /** Check if element is new line */
    @JvmStatic
    fun isNewLine(element: PsiElement): Boolean {
      return element.hasType(MarkdownTokenTypeSets.WHITE_SPACES) && element.text == "\n"
    }

    /** Check if element is whitespace -- not a new line, not `>` blockquote */
    @JvmStatic
    fun isWhiteSpace(element: PsiElement): Boolean {
      return element.hasType(MarkdownTokenTypeSets.WHITE_SPACES) && element.text.all { it.isWhitespace() && it != '\n' }
    }
  }


  @JvmField
  val PRESENTABLE_TYPES: TokenSet = MarkdownTokenTypeSets.HEADERS

  @JvmField
  val TRANSPARENT_CONTAINERS = TokenSet.create(
    MarkdownElementTypes.MARKDOWN_FILE,
    MarkdownElementTypes.UNORDERED_LIST,
    MarkdownElementTypes.ORDERED_LIST, MarkdownElementTypes.LIST_ITEM,
    MarkdownElementTypes.BLOCK_QUOTE)

  private val HEADER_ORDER = listOf(
    TokenSet.create(MarkdownElementTypes.MARKDOWN_FILE_ELEMENT_TYPE),
    MarkdownTokenTypeSets.HEADER_LEVEL_1_SET,
    MarkdownTokenTypeSets.HEADER_LEVEL_2_SET,
    MarkdownTokenTypeSets.HEADER_LEVEL_3_SET,
    MarkdownTokenTypeSets.HEADER_LEVEL_4_SET,
    MarkdownTokenTypeSets.HEADER_LEVEL_5_SET,
    MarkdownTokenTypeSets.HEADER_LEVEL_6_SET)

  fun findNonWhiteSpacePrevSibling(file: PsiFile, offset: Int): PsiElement? {
    var offset = offset
    while (offset > 0) {
      val element = file.findElementAt(offset)
      if (element == null) {
        offset--
        continue
      }
      if (!MarkdownTokenTypeSets.WHITE_SPACES.contains(element.node.elementType)) {
        return element
      }
      val newOffset = element.textOffset
      if (newOffset < offset) {
        offset = newOffset
      }
      else {
        offset--
      }
    }
    return null
  }

  /*
   * nextHeaderConsumer 'null' means reaching EOF
   */
  @JvmStatic
  fun processContainer(myElement: PsiElement?,
                       consumer: Consumer<in PsiElement>,
                       nextHeaderConsumer: NullableConsumer<in PsiElement>) {
    if (myElement == null) return
    val structureContainer = (if (myElement is MarkdownFile) myElement.getFirstChild()
    else getParentOfType(myElement, TRANSPARENT_CONTAINERS))
                             ?: return
    val currentHeader: MarkdownPsiElement? = if (myElement is MarkdownHeaderImpl) myElement else null
    processContainer(structureContainer, currentHeader, currentHeader, consumer, nextHeaderConsumer)
  }

  private fun processContainer(container: PsiElement,
                               sameLevelRestriction: PsiElement?,
                               from: MarkdownPsiElement?,
                               resultConsumer: Consumer<in PsiElement>,
                               nextHeaderConsumer: NullableConsumer<in PsiElement>) {
    var nextSibling = if (from == null) container.firstChild else from.nextSibling
    var maxContentLevel: PsiElement? = null
    while (nextSibling != null) {
      if (TRANSPARENT_CONTAINERS.contains(PsiUtilCore.getElementType(nextSibling)) && maxContentLevel == null) {
        processContainer(nextSibling, null, null, resultConsumer, nextHeaderConsumer)
      }
      else if (nextSibling is MarkdownHeaderImpl) {
        if (sameLevelRestriction != null && isSameLevelOrHigher(nextSibling, sameLevelRestriction)) {
          nextHeaderConsumer.consume(nextSibling)
          break
        }
        if (maxContentLevel == null || isSameLevelOrHigher(nextSibling, maxContentLevel)) {
          maxContentLevel = nextSibling
          val type = nextSibling.node.elementType
          if (PRESENTABLE_TYPES.contains(type)) {
            resultConsumer.consume(nextSibling)
          }
        }
      }
      nextSibling = nextSibling.nextSibling
      if (nextSibling == null) nextHeaderConsumer.consume(null)
    }
  }

  private fun isSameLevelOrHigher(psiA: PsiElement, psiB: PsiElement): Boolean {
    val typeA = psiA.node.elementType
    val typeB = psiB.node.elementType
    return headerLevel(typeA) <= headerLevel(typeB)
  }

  private fun headerLevel(curLevelType: IElementType): Int {
    for (i in HEADER_ORDER.indices) {
      if (HEADER_ORDER[i].contains(curLevelType)) {
        return i
      }
    }

    // not a header so return lowest level
    return Int.MAX_VALUE
  }

  private fun getParentOfType(myElement: PsiElement, types: TokenSet): PsiElement? {
    val parentNode = TreeUtil.findParent(myElement.node, types)
    return parentNode?.psi
  }
}
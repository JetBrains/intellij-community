package org.intellij.plugins.markdown.util

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.TreeUtil
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.Consumer
import com.intellij.util.NullableConsumer
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownList
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownListItem

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
    MarkdownElementTypes.ORDERED_LIST,
    MarkdownElementTypes.LIST_ITEM,
    MarkdownElementTypes.BLOCK_QUOTE)

  private val PRESENTABLE_CONTAINERS = TokenSet.create(
    MarkdownElementTypes.UNORDERED_LIST,
    MarkdownElementTypes.ORDERED_LIST)

  private val IGNORED_CONTAINERS = TokenSet.create(MarkdownElementTypes.BLOCK_QUOTE)

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

  @JvmStatic
  fun isSimpleNestedList(itemChildren: Array<PsiElement>) =
    itemChildren.size == 2 &&
    PsiUtilCore.getElementType(itemChildren[0]) == MarkdownElementTypes.PARAGRAPH &&
    itemChildren[1] is MarkdownList

  /*
   * nextHeaderConsumer 'null' means reaching EOF
   */
  @JvmStatic
  fun processContainer(myElement: PsiElement?,
                       consumer: Consumer<in PsiElement>,
                       nextHeaderConsumer: NullableConsumer<in PsiElement>) {
    if (myElement == null) return
    val structureContainer = (if (myElement is MarkdownFile) findFirstChild(myElement)
    else getParentOfType(myElement, TRANSPARENT_CONTAINERS))
                             ?: return

    val isListsVisible = Registry.`is`("markdown.structure.view.list.visibility")
    when {
      myElement is MarkdownHeader -> processHeader(structureContainer, myElement, myElement, consumer, nextHeaderConsumer)
      myElement is MarkdownList && isListsVisible -> processList(myElement, consumer)
      myElement is MarkdownListItem && isListsVisible -> {
        if (!myElement.hasTrivialChildren()) {
          processListItem(myElement, consumer)
        }
      }
      else -> processHeader(structureContainer, null, null, consumer, nextHeaderConsumer)
    }
  }

  private fun findFirstChild(myElement: PsiElement): PsiElement? {
    return myElement.children.asSequence().firstOrNull { it.language == MarkdownLanguage.INSTANCE }
  }

  private fun processHeader(container: PsiElement,
                            sameLevelRestriction: PsiElement?,
                            from: PsiElement?,
                            resultConsumer: Consumer<in PsiElement>,
                            nextHeaderConsumer: NullableConsumer<in PsiElement>) {
    var nextSibling = if (from == null) container.firstChild else from.nextSibling
    var maxContentLevel: PsiElement? = null

    while (nextSibling != null) {

      when {
        nextSibling.isTransparentInPartial() && maxContentLevel == null -> {
          processHeader(nextSibling, null, null, resultConsumer, nextHeaderConsumer)
        }
        nextSibling.isTransparentInFull() && maxContentLevel == null -> {
          if (!IGNORED_CONTAINERS.contains(PsiUtilCore.getElementType(container)) &&
              PRESENTABLE_CONTAINERS.contains(PsiUtilCore.getElementType(nextSibling))) {
            resultConsumer.consume(nextSibling)
          }
          processHeader(nextSibling, null, null, resultConsumer, nextHeaderConsumer)
        }
        nextSibling is MarkdownHeader -> {
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
      }

      nextSibling = nextSibling.nextSibling
      if (nextSibling == null) nextHeaderConsumer.consume(null)
    }
  }

  private fun processList(from: PsiElement,
                          resultConsumer: Consumer<in PsiElement>) {
    var listItem = from.firstChild

    while (listItem != null) {
      val itemChildren = listItem.children
      val isContainerIsFirst = (itemChildren.isNotEmpty() && PRESENTABLE_TYPES.contains(itemChildren[0].node.elementType)) ||
                               (itemChildren.size == 1 && PRESENTABLE_CONTAINERS.contains(PsiUtilCore.getElementType(itemChildren[0])))

      when {
        isContainerIsFirst -> resultConsumer.consume(itemChildren[0])
        isSimpleNestedList(itemChildren) -> resultConsumer.consume(itemChildren[1])
        listItem is MarkdownListItem -> resultConsumer.consume(listItem)
      }

      listItem = listItem.nextSibling
    }
  }

  private fun processListItem(from: PsiElement,
                              resultConsumer: Consumer<in PsiElement>) {
    var itemChild = from.firstChild

    while (itemChild != null) {
      if (PRESENTABLE_TYPES.contains(itemChild.node.elementType)) {
        resultConsumer.consume(itemChild)
        break
      }
      else if (PRESENTABLE_CONTAINERS.contains(PsiUtilCore.getElementType(itemChild))) {
        resultConsumer.consume(itemChild)
      }

      itemChild = itemChild.nextSibling
    }
  }

  /**
   * Returns true if the key of the lists representation in the structure is true
   * and the processed element is a transparent container, but not a list item.
   * Returns false otherwise.
   */
  private fun PsiElement.isTransparentInFull() =
    Registry.`is`("markdown.structure.view.list.visibility") &&
    TRANSPARENT_CONTAINERS.contains(PsiUtilCore.getElementType(this)) &&
    this !is MarkdownListItem

  /**
   * Returns true if the key of the lists representation in the structure is false (this means that only headers are shown in the structure view)
   * and the processed element is a transparent container.
   * Returns false otherwise.
   */
  private fun PsiElement.isTransparentInPartial() =
    !Registry.`is`("markdown.structure.view.list.visibility") &&
    TRANSPARENT_CONTAINERS.contains(PsiUtilCore.getElementType(this))


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

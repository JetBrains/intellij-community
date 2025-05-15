package org.intellij.plugins.markdown.util

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.elementType
import com.intellij.psi.util.siblings
import com.intellij.util.Consumer
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownList
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownListItem
import org.intellij.plugins.markdown.lang.psi.util.children
import org.intellij.plugins.markdown.lang.psi.util.hasType
import org.intellij.plugins.markdown.lang.psi.util.parentOfType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object MarkdownPsiStructureUtil {
  @JvmField
  val PRESENTABLE_TYPES: TokenSet = MarkdownTokenTypeSets.HEADERS

  @JvmField
  val TRANSPARENT_CONTAINERS = TokenSet.create(
    MarkdownElementTypes.MARKDOWN_FILE_ELEMENT_TYPE,
    //MarkdownElementTypes.MARKDOWN_FILE,
    MarkdownElementTypes.UNORDERED_LIST,
    MarkdownElementTypes.ORDERED_LIST,
    MarkdownElementTypes.LIST_ITEM,
    MarkdownElementTypes.BLOCK_QUOTE
  )

  private val PRESENTABLE_CONTAINERS = TokenSet.create(
    MarkdownElementTypes.UNORDERED_LIST,
    MarkdownElementTypes.ORDERED_LIST
  )

  private val IGNORED_CONTAINERS = TokenSet.create(MarkdownElementTypes.BLOCK_QUOTE)

  private val HEADER_ORDER = arrayOf(
    TokenSet.create(MarkdownElementTypes.MARKDOWN_FILE_ELEMENT_TYPE),
    MarkdownTokenTypeSets.HEADER_LEVEL_1_SET,
    MarkdownTokenTypeSets.HEADER_LEVEL_2_SET,
    MarkdownTokenTypeSets.HEADER_LEVEL_3_SET,
    MarkdownTokenTypeSets.HEADER_LEVEL_4_SET,
    MarkdownTokenTypeSets.HEADER_LEVEL_5_SET,
    MarkdownTokenTypeSets.HEADER_LEVEL_6_SET
  )

  /**
   * @return true if element is on the file top level.
   */
  @ApiStatus.Internal
  fun ASTNode.isTopLevel(): Boolean {
    return treeParent?.hasType(MarkdownElementTypes.MARKDOWN_FILE_ELEMENT_TYPE) == true
  }

  @JvmStatic
  fun isSimpleNestedList(itemChildren: Array<PsiElement>): Boolean {
    if (itemChildren.size != 2) {
      return false
    }
    val (firstItem, secondItem) = itemChildren
    return firstItem.hasType(MarkdownElementTypes.PARAGRAPH) && secondItem is MarkdownList
  }

  /*
   * nextHeaderConsumer 'null' means reaching EOF
   */
  @JvmOverloads
  @JvmStatic
  fun processContainer(
    element: PsiElement?,
    consumer: Consumer<PsiElement>,
    nextHeaderConsumer: Consumer<PsiElement?>? = null
  ) {
    if (element == null) {
      return
    }
    val structureContainer = when (element) {
      is MarkdownFile -> element
      else -> element.parentOfType(withSelf = true, TRANSPARENT_CONTAINERS)
    }
    if (structureContainer == null) {
      return
    }
    val areListsVisible = Registry.`is`("markdown.structure.view.list.visibility")
    when {
      element is MarkdownHeader -> processHeader(structureContainer, element, element, consumer, nextHeaderConsumer)
      element is MarkdownList && areListsVisible -> processList(element, consumer)
      element is MarkdownListItem && areListsVisible -> {
        if (!element.hasTrivialChildren()) {
          processListItem(element, consumer)
        }
      }
      else -> processHeader(structureContainer, null, null, consumer, nextHeaderConsumer)
    }
  }

  private fun processHeader(
    container: PsiElement,
    sameLevelRestriction: PsiElement?,
    from: PsiElement?,
    resultConsumer: Consumer<PsiElement>,
    nextHeaderConsumer: Consumer<PsiElement?>?
  ) {
    val startElement = when (from) {
      null -> container.firstChild
      else -> from.nextSibling
    }
    var maxContentLevel: PsiElement? = null
    for (element in startElement?.siblings(forward = true, withSelf = true).orEmpty()) {
      when {
        element.isTransparentInPartial() && maxContentLevel == null -> {
          processHeader(element, null, null, resultConsumer, nextHeaderConsumer)
        }
        element.isTransparentInFull() && maxContentLevel == null -> {
          if (container.elementType !in IGNORED_CONTAINERS && element.elementType in PRESENTABLE_CONTAINERS) {
            resultConsumer.consume(element)
          }
          processHeader(element, null, null, resultConsumer, nextHeaderConsumer)
        }
        element is MarkdownHeader -> {
          if (sameLevelRestriction != null && isSameLevelOrHigher(element, sameLevelRestriction)) {
            nextHeaderConsumer?.consume(element)
            return
          }
          if (maxContentLevel == null || isSameLevelOrHigher(element, maxContentLevel)) {
            maxContentLevel = element
            if (element.elementType in PRESENTABLE_TYPES) {
              resultConsumer.consume(element)
            }
          }
        }
      }
    }
    nextHeaderConsumer?.consume(null)
  }

  private fun processList(list: MarkdownList, resultConsumer: Consumer<in PsiElement>) {
    for (child in list.children()) {
      val children = child.children
      val containerIsFirst = children.firstOrNull()?.elementType in PRESENTABLE_TYPES || children.singleOrNull()?.elementType in PRESENTABLE_CONTAINERS
      when {
        containerIsFirst -> resultConsumer.consume(children.first())
        isSimpleNestedList(children) -> resultConsumer.consume(children[1])
        child is MarkdownListItem -> resultConsumer.consume(child)
      }
    }
  }

  private fun processListItem(from: MarkdownListItem, resultConsumer: Consumer<in PsiElement>) {
    for (child in from.children()) {
      if (child.elementType in PRESENTABLE_TYPES) {
        resultConsumer.consume(child)
        break
      }
      if (child.elementType in PRESENTABLE_CONTAINERS) {
        resultConsumer.consume(child)
      }
    }
  }

  /**
   * Returns true if the key of the lists representation in the structure is true
   * and the processed element is a transparent container, but not a list item.
   * Returns false otherwise.
   */
  private fun PsiElement.isTransparentInFull(): Boolean {
    if (!Registry.`is`("markdown.structure.view.list.visibility")) {
      return false
    }
    return elementType in TRANSPARENT_CONTAINERS && this !is MarkdownListItem
  }

  /**
   * Returns true if the key of the lists representation in the structure is false (this means that only headers are shown in the structure view)
   * and the processed element is a transparent container.
   * Returns false otherwise.
   */
  private fun PsiElement.isTransparentInPartial(): Boolean {
    return !Registry.`is`("markdown.structure.view.list.visibility") && elementType in TRANSPARENT_CONTAINERS
  }

  private fun isSameLevelOrHigher(left: PsiElement, right: PsiElement): Boolean {
    return obtainHeaderLevel(left.elementType!!) <= obtainHeaderLevel(right.elementType!!)
  }

  private fun obtainHeaderLevel(headerType: IElementType): Int {
    return when (val index = HEADER_ORDER.indexOfFirst { headerType in it }) {
      -1 -> Int.MAX_VALUE
      else -> index
    }
  }
}

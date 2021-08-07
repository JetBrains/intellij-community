package org.jetbrains.yaml.psi.impl

import com.intellij.codeInsight.intention.impl.reuseFragmentEditorIndent
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.SmartList
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.tailOrEmpty
import com.intellij.util.text.splitLineRanges
import org.jetbrains.yaml.YAMLElementTypes
import org.jetbrains.yaml.YAMLTokenTypes
import org.jetbrains.yaml.YAMLUtil
import org.jetbrains.yaml.psi.YAMLBlockScalar
import kotlin.math.min

abstract class YAMLBlockScalarImpl(node: ASTNode) : YAMLScalarImpl(node), YAMLBlockScalar {
  protected abstract val contentType: IElementType

  override fun isMultiline(): Boolean = true

  override fun getContentRanges(): List<TextRange> = CachedValuesManager.getCachedValue(this, CachedValueProvider {
    val myStart = textRange.startOffset
    val indent = locateIndent()

    val contentRanges = linesNodes.asSequence().mapNotNull { line ->
      val first = line.first()
      val start = (first.textRange.startOffset - myStart
                   + if (first.elementType == YAMLTokenTypes.INDENT) min(first.textLength, indent) else 0)
      val end = line.last().textRange.endOffset - myStart
      if (start <= end)
        TextRange.create(start, end)
      else null
    }.fold(SmartList<TextRange>()) { list, range ->
      list.apply {
        if (size > 1 && last().endOffset == range.startOffset)
          set(lastIndex, TextRange(last().startOffset, range.endOffset))
        else
          add(range)
      }
    }

    CachedValueProvider.Result.create((if (contentRanges.size == 1)
      listOf(contentRanges.single().let { TextRange.create(it.endOffset, it.endOffset) })
    else if (contentRanges.isEmpty())
      emptyList()
    else
      contentRanges.tailOrEmpty()), PsiModificationTracker.MODIFICATION_COUNT)
  })

  fun hasExplicitIndent(): Boolean = explicitIndent != IMPLICIT_INDENT

  /**
   * @return Nth child of this scalar block item type ([YAMLElementTypes.BLOCK_SCALAR_ITEMS]).
   * Child with number 0 is a header. Content children have numbers more than 0.
   */
  fun getNthContentTypeChild(nth: Int): ASTNode? {
    var number = 0
    var child = node.firstChildNode
    while (child != null) {
      if (child.elementType === contentType) {
        if (number == nth) {
          return child
        }
        number++
      }
      child = child.treeNext
    }
    return null
  }

  /** See [8.1.1.1. Block Indentation Indicator](http://www.yaml.org/spec/1.2/spec.html#id2793979) */
  fun locateIndent(): Int = reuseFragmentEditorIndent(this, fun(): Int {
    val indent = explicitIndent
    if (indent != IMPLICIT_INDENT) {
      return indent
    }
    val firstLine = getNthContentTypeChild(1)
    if (firstLine != null) {
      return YAMLUtil.getIndentInThisLine(firstLine.psi)
    }
    else {
      val line = linesNodes.getOrNull(1)
      if (line != null) {
        val lineIndentElement = ContainerUtil.find(line) { l: ASTNode -> l.elementType == YAMLTokenTypes.INDENT }
        if (lineIndentElement != null) {
          return lineIndentElement.textLength
        }
      }
    }
    return 0
  }) ?: IMPLICIT_INDENT

  @Throws(IllegalArgumentException::class)
  override fun getEncodeReplacements(input: CharSequence): List<Pair<TextRange, String>> {
    var indent = locateIndent()
    if (indent == 0) {
      indent = YAMLUtil.getIndentToThisElement(this) + DEFAULT_CONTENT_INDENT
    }
    val indentString = StringUtil.repeatSymbol(' ', indent)

    return splitLineRanges(input).zipWithNext { a, b -> Pair.create(TextRange.create(a.endOffset, b.startOffset), indentString) }.toList()
  }

  protected val linesNodes: List<List<ASTNode>>
    get() {
      val result: MutableList<List<ASTNode>> = SmartList()
      var currentLine: MutableList<ASTNode> = SmartList()
      var child = node.firstChildNode
      while (child != null) {
        currentLine.add(child)
        if (isEol(child)) {
          result.add(currentLine)
          currentLine = SmartList()
        }
        child = child.treeNext
      }
      if (!currentLine.isEmpty()) {
        result.add(currentLine)
      }
      return result
    }
  
  // YAML 1.2 standard does not allow more then 1 symbol in indentation number
  private val explicitIndent: Int
    get() {
      val headerNode = getNthContentTypeChild(0)!!
      val header = headerNode.text
      for (i in 0 until header.length) {
        if (Character.isDigit(header[i])) {
          val k = i + 1
          // YAML 1.2 standard does not allow more then 1 symbol in indentation number
          if (k < header.length && Character.isDigit(header[k])) {
            return IMPLICIT_INDENT
          }
          val res = header.substring(i, k).toInt()
          return if (res == 0) {
            // zero is not allowed as c-indentation-indicator
            IMPLICIT_INDENT
          }
          else res
        }
      }
      return IMPLICIT_INDENT
    }
  
}

const val DEFAULT_CONTENT_INDENT = 2
private const val IMPLICIT_INDENT = -1

fun isEol(node: ASTNode?): Boolean = when (node) {
  null -> false
  else -> YAMLElementTypes.EOL_ELEMENTS.contains(node.elementType)
}

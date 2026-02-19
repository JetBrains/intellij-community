//package org.jetbrains.plugins.textmate.editor
//
//import com.intellij.formatting.*
//import com.intellij.openapi.util.TextRange
//import com.intellij.psi.PsiElement
//
///**
// * A formatting model defines how a file is broken into non-whitespace blocks and different types of whitespace (alignment, indents, and wraps) between them.
// * Here is helps `DetectableIndentOptionsProvider` to autodetect actual indents used in the file
// */
//class TextMateFormattingModelBuilder: FormattingModelBuilder {
//  override fun createModel(formattingContext: FormattingContext): FormattingModel {
//    return Formatter.getInstance().createFormattingModelForPsiFile(formattingContext.containingFile,
//                                                                   TextMateFormattingBlock(formattingContext.psiElement, null, null, formattingContext),
//                                                                   formattingContext.codeStyleSettings)
//  }
//  class TextMateFormattingBlock(private val psiElement: PsiElement, private val wrap: Wrap?, private val alignment: Alignment?, val context: FormattingContext)
//    : Block {
//
//    override fun getTextRange(): TextRange {
//      return psiElement.textRange
//    }
//
//    override fun getSubBlocks(): MutableList<out Block> {
//      return mutableListOf()
//    }
//
//    override fun getWrap(): Wrap? {
//      return wrap
//    }
//
//    override fun getIndent(): Indent? {
//      return null
//    }
//
//    override fun getAlignment(): Alignment? {
//      return alignment
//    }
//
//    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
//      return null
//    }
//
//    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
//      return ChildAttributes(null, null)
//    }
//
//    override fun isIncomplete(): Boolean {
//      return true
//    }
//
//    override fun isLeaf(): Boolean {
//      return psiElement.firstChild == null
//    }
//  }
//}
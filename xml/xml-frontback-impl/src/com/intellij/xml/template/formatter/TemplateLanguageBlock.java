// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.template.formatter;

import com.intellij.formatting.*;
import com.intellij.formatting.templateLanguages.BlockWithParent;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.formatter.xml.XmlFormattingPolicy;
import com.intellij.psi.xml.XmlDocument;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class TemplateLanguageBlock extends AbstractBlock implements BlockEx, IndentInheritingBlock, BlockWithParent {
  private final CodeStyleSettings mySettings;
  private final AbstractXmlTemplateFormattingModelBuilder myBuilder;
  private final XmlFormattingPolicy myXmlFormattingPolicy;
  private @Nullable Indent myIndent;
  private BlockWithParent myParent;
  private boolean myContainsErrorElements = false;

  protected TemplateLanguageBlock(AbstractXmlTemplateFormattingModelBuilder builder,
                                  @NotNull ASTNode node,
                                  @Nullable Wrap wrap,
                                  @Nullable Alignment alignment,
                                  CodeStyleSettings settings,
                                  XmlFormattingPolicy xmlFormattingPolicy,
                                  @Nullable Indent indent) {
    super(node, wrap, alignment);
    mySettings = settings;
    myBuilder = builder;
    myXmlFormattingPolicy = xmlFormattingPolicy;
    myIndent = indent;
  }

  protected List<Block> buildChildrenWithMerge() throws FragmentedTemplateException {
    final List<Block> markupBlocks = new ArrayList<>();
    List<PsiElement> markupElements = TemplateFormatUtil.findAllMarkupLanguageElementsInside(myNode.getPsi());
    if (markupElements.size() == 1 && markupElements.get(0) instanceof XmlDocument) {
      markupElements = getXmlDocumentChildren(markupElements.get(0));
    }
    boolean mergeFromMarkup = false;
    for (PsiElement markupElement : markupElements) {
      if (TemplateFormatUtil.isErrorElement(markupElement)) {
        throw new FragmentedTemplateException((PsiErrorElement)markupElement);
      }
      if (!(FormatterUtil.containsWhiteSpacesOnly(markupElement.getNode()))) {
        Block rootBlock = myBuilder
          .createDataLanguageRootBlock(markupElement, markupElement.getLanguage(), mySettings, myXmlFormattingPolicy,
                                       myNode.getPsi().getContainingFile(), getDefaultMarkupIndent());
        PsiElement parent = markupElement.getParent();
        if (!mergeFromMarkup) mergeFromMarkup = isScriptBlock(rootBlock);
        if (parent instanceof PsiFile ||
            (rootBlock instanceof TemplateXmlBlock && ((TemplateXmlBlock)rootBlock).isTextContainingTemplateElements())) {
          for (Block block : rootBlock.getSubBlocks()) {
            if (containsErrorElement(block)) {
              throw new FragmentedTemplateException();
            }
            markupBlocks.add(block);
          }
        }
        else {
          markupBlocks.add(rootBlock);
        }
      }
    }
    List<Block> result = new ArrayList<>();
    ASTNode child = myNode.getFirstChildNode();
    while (child != null) {
      if (containsFatalError(child.getPsi()) && !markupBlocks.isEmpty()) {
        throw new FragmentedTemplateException();
      }
      if (!FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0) {
        if (!myBuilder.isMarkupLanguageElement(child.getPsi())) {
          addBlocksForNonMarkupChild(result, child);
        }
      }
      child = child.getTreeNext();
    }
    if (!markupBlocks.isEmpty()) {
      if (result.isEmpty()) return markupBlocks;
      if (mergeFromMarkup) {
        result = TemplateFormatUtil.mergeBlocks(markupBlocks, result, myNode.getTextRange());
      }
      else {
        result = TemplateFormatUtil.mergeBlocks(result, markupBlocks, myNode.getTextRange());
      }
      for (Block resultBlock : result) {
        ASTNode node = resultBlock instanceof ASTBlock ? ((ASTBlock)resultBlock).getNode() : null;
        if (node != null && resultBlock instanceof IndentInheritingBlock) {
          ((IndentInheritingBlock)resultBlock).setIndent(getChildIndent(node));
        }
        if (resultBlock instanceof BlockWithParent) {
          ((BlockWithParent)resultBlock).setParent(this);
        }
      }
    }
    return result;
  }

  /**
   * Checks that the given element contains fatal syntax errors which mean that the whole fragment is damaged and no reliable formatting
   * is possible, {@code false} by default but can be overridden in subclasses for specific logic/heuristics.
   *
   * @param element The element to check.
   * @return True if the fragment can't be reliably processed.
   */
  public boolean containsFatalError(@NotNull PsiElement element) {
    return false;
  }

  @Override
  protected List<Block> buildChildren() {
    try {
      return buildChildrenWithMerge();
    }
    catch (FragmentedTemplateException e) {
      myContainsErrorElements = true;
      return AbstractBlock.EMPTY;
    }
  }

  private @NotNull List<PsiElement> getXmlDocumentChildren(@NotNull PsiElement xmlDocument) {
    List<PsiElement> children = new ArrayList<>();
    PsiElement child = xmlDocument.getFirstChild();
    while (child != null) {
      if (!myBuilder.isOuterLanguageElement(child)) {
        children.add(child);
      }
      child = child.getNextSibling();
    }
    return children;
  }

  private static boolean containsErrorElement(@NotNull Block block) {
    if (block instanceof ASTBlock) {
      ASTNode node = ((ASTBlock)block).getNode();
      if (node != null) {
        return TemplateFormatUtil.isErrorElement(node.getPsi());
      }
    }
    return false;
  }

  protected void addBlocksForNonMarkupChild(List<Block> result, ASTNode child) {
    Block templateLanguageBlock = createTemplateLanguageBlock(child);
    if (templateLanguageBlock instanceof BlockWithParent) {
      ((BlockWithParent)templateLanguageBlock).setParent(this);
    }
    result.add(templateLanguageBlock);
  }

  protected Block createTemplateLanguageBlock(ASTNode child) {
    return myBuilder.createTemplateLanguageBlock(
      child,
      mySettings,
      myXmlFormattingPolicy,
      getChildIndent(child),
      getChildAlignment(child),
      getChildWrap(child)
    );
  }

  protected @NotNull AbstractXmlTemplateFormattingModelBuilder getBuilder() {
    return myBuilder;
  }

  private static boolean isScriptBlock(Block block) {
    if (block instanceof TemplateXmlTagBlock) {
      return ((TemplateXmlTagBlock)block).isScriptBlock();
    }
    return false;
  }

  protected @Nullable Alignment getChildAlignment(ASTNode child) {
    return null;
  }

  @Override
  public @Nullable Indent getIndent() {
    return myIndent;
  }

  @Override
  protected final Indent getChildIndent() {
    return Indent.getNoneIndent();
  }

  protected abstract @NotNull Indent getChildIndent(@NotNull ASTNode node);

  @Override
  public boolean isLeaf() {
    return myNode.getFirstChildNode() == null || myContainsErrorElements;
  }

  @Override
  public void setIndent(@Nullable Indent indent) {
    myIndent = indent;
  }

  @Override
  public @NotNull ChildAttributes getChildAttributes(int newChildIndex) {
    return new ChildAttributes(Indent.getNormalIndent(), null);
  }

  protected Indent getDefaultMarkupIndent() {
    return Indent.getNormalIndent();
  }

  public CodeStyleSettings getSettings() {
    return mySettings;
  }

  @Override
  public BlockWithParent getParent() {
    return myParent;
  }

  @Override
  public void setParent(BlockWithParent newParent) {
    myParent = newParent;
  }

  protected @Nullable Wrap getChildWrap(ASTNode child) {
    return Wrap.createWrap(WrapType.NONE, false);
  }

  protected abstract Spacing getSpacing(TemplateLanguageBlock adjacentBlock);

  public XmlFormattingPolicy getXmlFormattingPolicy() {
    return myXmlFormattingPolicy;
  }

  public boolean containsErrorElements() {
    return myContainsErrorElements;
  }

  @Override
  public @Nullable Language getLanguage() {
    return myNode.getPsi().getLanguage();
  }
}

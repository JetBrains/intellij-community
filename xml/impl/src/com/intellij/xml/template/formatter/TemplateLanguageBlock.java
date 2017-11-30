/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  protected final ASTNode myNode;
  private final CodeStyleSettings mySettings;
  private final AbstractXmlTemplateFormattingModelBuilder myBuilder;
  private XmlFormattingPolicy myXmlFormattingPolicy;
  private Indent myIndent;
  private BlockWithParent myParent;
  private boolean myContainsErrorElements = false;

  protected TemplateLanguageBlock(AbstractXmlTemplateFormattingModelBuilder builder,
                                  @NotNull ASTNode node,
                                  @Nullable Wrap wrap,
                                  @Nullable Alignment alignment,
                                  CodeStyleSettings settings,
                                  XmlFormattingPolicy xmlFormattingPolicy,
                                  Indent indent) {
    super(node, wrap, alignment);
    myNode = node;
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
      if (containsFatalError(child.getPsi()) && markupBlocks.size() > 0) {
        throw new FragmentedTemplateException();
      }
      if (!FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0) {
        if (!myBuilder.isMarkupLanguageElement(child.getPsi())) {
          addBlocksForNonMarkupChild(result, child);
        }
      }
      child = child.getTreeNext();
    }
    if (markupBlocks.size() > 0) {
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

  @NotNull
  private List<PsiElement> getXmlDocumentChildren(@NotNull PsiElement xmlDocument) {
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
    Block templateLanguageBlock = myBuilder.createTemplateLanguageBlock(
      child,
      mySettings,
      myXmlFormattingPolicy,
      getChildIndent(child),
      getChildAlignment(child),
      getChildWrap(child)
    );
    if (templateLanguageBlock instanceof BlockWithParent) {
      ((BlockWithParent)templateLanguageBlock).setParent(this);
    }
    result.add(templateLanguageBlock);
  }


  private static boolean isScriptBlock(Block block) {
    if (block instanceof TemplateXmlTagBlock) {
      return ((TemplateXmlTagBlock)block).isScriptBlock();
    }
    return false;
  }

  @SuppressWarnings("MethodMayBeStatic")
  @Nullable
  protected Alignment getChildAlignment(@SuppressWarnings("UnusedParameters") ASTNode child) {
    return null;
  }

  @Override
  public Indent getIndent() {
    return myIndent;
  }

  @Override
  protected final Indent getChildIndent() {
    return Indent.getNoneIndent();
  }

  @NotNull
  protected abstract Indent getChildIndent(@NotNull ASTNode node);

  @Override
  public boolean isLeaf() {
    return myNode.getFirstChildNode() == null || myContainsErrorElements;
  }

  @Override
  public void setIndent(Indent indent) {
    myIndent = indent;
  }

  @NotNull
  @Override
  public ChildAttributes getChildAttributes(int newChildIndex) {
    return new ChildAttributes(Indent.getNormalIndent(), null);
  }

  @SuppressWarnings("MethodMayBeStatic")
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

  @Nullable
  @SuppressWarnings("MethodMayBeStatic")
  protected Wrap getChildWrap(@SuppressWarnings("UnusedParameters") ASTNode child) {
    return Wrap.createWrap(WrapType.NONE, false);
  }

  protected abstract Spacing getSpacing(TemplateLanguageBlock adjacentBlock);

  public XmlFormattingPolicy getXmlFormattingPolicy() {
    return myXmlFormattingPolicy;
  }

  public boolean containsErrorElements() {
    return myContainsErrorElements;
  }

  @Nullable
  @Override
  public Language getLanguage() {
    return myNode.getPsi().getLanguage();
  }
}

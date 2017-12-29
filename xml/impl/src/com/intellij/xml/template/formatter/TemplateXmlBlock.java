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
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.formatter.xml.*;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.xml.XmlElementType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TemplateXmlBlock extends XmlBlock implements IndentInheritingBlock {
  private AbstractXmlTemplateFormattingModelBuilder myBuilder;
  private Indent myIndent;

  private final static List<Block> EMPTY_BLOCK_LIST = new ArrayList<>();

  public TemplateXmlBlock(final AbstractXmlTemplateFormattingModelBuilder builder,
                          final ASTNode node,
                          final Wrap wrap,
                          final Alignment alignment,
                          final XmlFormattingPolicy policy,
                          final Indent indent,
                          final TextRange textRange) {
    super(node, wrap, alignment, policy, indent, textRange);
    myBuilder = builder;
  }

  @Override
  protected XmlBlock createSimpleChild(ASTNode child, Indent indent, Wrap wrap, Alignment alignment) {
    return myBuilder.createXmlBlock(child, wrap, alignment, myXmlFormattingPolicy,indent, child.getTextRange());
  }

  @Override
  protected XmlTagBlock createTagBlock(ASTNode child, Indent indent, Wrap wrap, Alignment alignment) {
    return myBuilder.createXmlTagBlock(child, wrap, alignment, myXmlFormattingPolicy, indent);
  }

  @Override
  protected Indent getChildDefaultIndent() {
    Indent indent = super.getChildDefaultIndent();
    if (indent == null) {
      indent = Indent.getNoneIndent();
    }
    return indent;
  }

  protected List<Block> buildChildrenNoMerge() {
    return super.buildChildren();
  }

  @Override
  protected List<Block> buildChildren() {
    try {
      List<Block> childBlocks = patchTopLevelChildBlocks(buildChildrenNoMerge());
      return myBuilder.mergeWithTemplateBlocks(childBlocks, myXmlFormattingPolicy.getSettings(), myXmlFormattingPolicy, getChildDefaultIndent());
    }
    catch (FragmentedTemplateException fte) {
      return EMPTY_BLOCK_LIST;
    }
  }

  private List<Block> patchTopLevelChildBlocks(List<Block> originalBlocks) {
    if (myNode.getPsi() instanceof PsiFile) {
      List<Block> patchedBlocks = new ArrayList<>();
      for (Block block : originalBlocks) {
        if (block == originalBlocks.get(0) && block instanceof TemplateXmlBlock) {
          patchedBlocks.addAll(((TemplateXmlBlock)block).buildChildrenNoMerge());
        }
        else {
          patchedBlocks.add(block);
        }
      }
      return patchedBlocks;
    }
    else {
      return originalBlocks;
    }
  }

  @Override
  public void setIndent(Indent indent) {
    myIndent = indent;
  }

  @Override
  public Indent getIndent() {
    return myIndent != null ? myIndent : super.getIndent();
  }

  @Override
  public Spacing getSpacing(Block child1, @NotNull Block child2) {
    if (child1 instanceof TemplateLanguageBlock && child2 instanceof TemplateLanguageBlock) {
      return ((TemplateLanguageBlock)child1).getSpacing((TemplateLanguageBlock)child2);
    }
    if (child1 instanceof TemplateLanguageBlock || child2 instanceof TemplateLanguageBlock) {
      return Spacing.createSpacing(0, Integer.MAX_VALUE, 0, true, myXmlFormattingPolicy.getKeepBlankLines());
    }
    return super.getSpacing(child1, child2);
  }

  public boolean isTextContainingTemplateElements() {
    if (isTextElement()) {
      for (ASTNode child = myNode.getFirstChildNode(); child != null; child = child.getTreeNext()) {
        if (myBuilder.isOuterLanguageElement(child.getPsi())) return true;
      }
    }
    return false;
  }

  @Override
  protected List<Block> splitComment() {
    if (myNode.getElementType() != XmlElementType.XML_COMMENT) return EMPTY;
    final ArrayList<Block> result = new ArrayList<>(3);
    ASTNode child = myNode.getFirstChildNode();
    boolean hasOuterLangElements = false;
    while (child != null) {
      if (child instanceof OuterLanguageElement) {
        hasOuterLangElements = true;
      }
      if (myBuilder.isOuterLanguageElement(child.getPsi())) {
        result.add(createTemplateFragmentWrapper(child));
      }
      else {
        result.add(new XmlBlock(child, null, null, myXmlFormattingPolicy, getChildIndent(), null, isPreserveSpace()));
      }
      child = child.getTreeNext();
    }
    return hasOuterLangElements ? result : EMPTY;
  }

  private AnotherLanguageBlockWrapper createTemplateFragmentWrapper(@NotNull ASTNode child) {
    return new AnotherLanguageBlockWrapper(child, myXmlFormattingPolicy, new ReadOnlyBlock(child), null, child.getStartOffset(),
                                           child.getTextRange());
  }
}

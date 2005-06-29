package com.intellij.psi.formatter.xml;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.PsiElement;
import com.intellij.newCodeFormatting.Block;
import com.intellij.newCodeFormatting.FormattingModelBuilder;
import com.intellij.util.containers.HashMap;
import com.intellij.lang.Language;
import com.intellij.openapi.util.Pair;

import java.util.Map;

public abstract class XmlFormattingPolicy {

  private final Map<Pair<PsiElement, Language>, Block> myRootToBlockMap = new HashMap<Pair<PsiElement, Language>, Block>();
  private boolean myCanProcessJsp = true;

  public Block getOrCreateBlockFor(Pair<PsiElement, Language> root){
    if (!myRootToBlockMap.containsKey(root)) {
      myRootToBlockMap.put(root, createBlockFor(root));
    }
    return myRootToBlockMap.get(root);
  }

  private Block createBlockFor(final Pair<PsiElement,Language> root) {
    final FormattingModelBuilder builder = root.getSecond().getFormattingModelBuilder();
    if (builder != null) {
      final Block result = builder.createModel(root.getFirst(), getSettings()).getRootBlock();
      if (result instanceof XmlBlock) {
        ((XmlBlock)result).getPolicy().setCanProcessJsp(false);
      }
      return result;
    } else {
      return null;
    }

  }

  public abstract int getWrappingTypeForTagEnd(XmlTag xmlTag);

  public abstract int getWrappingTypeForTagBegin(final XmlTag tag);

  public abstract boolean insertLineBreakBeforeTag(XmlTag xmlTag);

  public abstract boolean removeLineBreakBeforeTag(XmlTag xmlTag);

  public abstract boolean keepWhiteSpacesInsideTag(XmlTag tag);

  public abstract boolean indentChildrenOf(XmlTag parentTag);

  public abstract IElementType getTagType();

  public abstract boolean isTextElement(XmlTag tag);

  public abstract int getTextWrap();

  public abstract int getAttributesWrap();

  public abstract boolean getShouldAlignAttributes();

  public abstract boolean getShouldAlignText();

  public abstract boolean getShouldKeepWhiteSpaces();

  public abstract boolean getShouldAddSpaceAroundEqualityInAttribute();

  public abstract boolean getShouldAddSpaceAroundTagName();

  public abstract int getKeepBlankLines();

  public abstract boolean getShouldKeepLineBreaks();

  public abstract CodeStyleSettings getSettings();

  public boolean processJsp() {
    return myCanProcessJsp;
  }

  public void setCanProcessJsp(final boolean canProcessJsp) {
    myCanProcessJsp = canProcessJsp;
  }

  public abstract boolean addSpaceIntoEmptyTag();
}

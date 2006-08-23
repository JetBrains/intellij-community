package com.intellij.psi.formatter.xml;

import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingDocumentModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.formatting.WrapType;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashMap;

import java.util.Map;

public abstract class XmlFormattingPolicy {

  private Map<Pair<PsiElement, Language>, Block> myRootToBlockMap = new HashMap<Pair<PsiElement, Language>, Block>();
  private boolean myProcessJsp = true;
  protected final FormattingDocumentModel myDocumentModel;
  private boolean myProcessJavaTree = true;

  protected XmlFormattingPolicy(final FormattingDocumentModel documentModel) {
    myDocumentModel = documentModel;
  }

  public void copyFrom(final XmlFormattingPolicy xmlFormattingPolicy) {
    myProcessJsp = xmlFormattingPolicy.myProcessJsp;
    myRootToBlockMap.putAll(xmlFormattingPolicy.myRootToBlockMap);
    myProcessJavaTree = xmlFormattingPolicy.processJavaTree(); 
  }

  public Block getOrCreateBlockFor(Pair<PsiElement, Language> root){
    if (!myRootToBlockMap.containsKey(root)) {
      myRootToBlockMap.put(root, createBlockFor(root));
    }
    return myRootToBlockMap.get(root);
  }

  private Block createBlockFor(final Pair<PsiElement,Language> root) {
    final FormattingModelBuilder builder = root.getSecond().getEffectiveFormattingModelBuilder(root.getFirst());
    if (builder != null) {
      final Block result = builder.createModel(root.getFirst(), getSettings()).getRootBlock();
      if (result instanceof XmlBlock) {
        final XmlFormattingPolicy policy = ((XmlBlock)result).getPolicy();
        policy.setRootModels(myRootToBlockMap);
        policy.doNotProcessJsp();
      }
      return result;
    } else {
      return null;
    }

  }

  private void doNotProcessJsp() {
    myProcessJsp = false;
  }

  private void setRootModels(final Map<Pair<PsiElement, Language>, Block> rootToBlockMap) {
    myRootToBlockMap = rootToBlockMap;
  }

  public abstract WrapType getWrappingTypeForTagEnd(XmlTag xmlTag);

  public abstract WrapType getWrappingTypeForTagBegin(final XmlTag tag);

  public abstract boolean insertLineBreakBeforeTag(XmlTag xmlTag);

  public abstract boolean removeLineBreakBeforeTag(XmlTag xmlTag);

  public abstract boolean keepWhiteSpacesInsideTag(XmlTag tag);

  public abstract boolean indentChildrenOf(XmlTag parentTag);

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

  public abstract boolean getShouldKeepLineBreaksInText();

  public abstract CodeStyleSettings getSettings();

  public boolean processJsp() {
    return myProcessJsp;
  }

  public abstract boolean addSpaceIntoEmptyTag();

  public void setRootBlock(final ASTNode node, final Block rootBlock) {
    myRootToBlockMap.put(new Pair<PsiElement, Language>(node.getPsi(), node.getPsi().getLanguage()), rootBlock);
  }

  public FormattingDocumentModel getDocumentModel() {
    return myDocumentModel;
  }

  public abstract boolean shouldSaveSpacesBetweenTagAndText();

  public boolean processJavaTree() {
    return myProcessJavaTree;
  }


  public void dontProcessJavaTree() {
    myProcessJavaTree = false;
  }
}

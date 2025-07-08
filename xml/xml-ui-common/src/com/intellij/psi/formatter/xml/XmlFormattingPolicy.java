// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.xml;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public abstract class XmlFormattingPolicy {

  private Map<Pair<PsiElement, Language>, Block> myRootToBlockMap = new HashMap<>();
  private boolean myProcessJsp = true;
  protected final FormattingDocumentModel myDocumentModel;
  private boolean myProcessJavaTree = true;

  protected XmlFormattingPolicy(@NotNull FormattingDocumentModel documentModel) {
    myDocumentModel = documentModel;
  }

  public void copyFrom(@NotNull XmlFormattingPolicy xmlFormattingPolicy) {
    myProcessJsp = xmlFormattingPolicy.myProcessJsp;
    myRootToBlockMap.putAll(xmlFormattingPolicy.myRootToBlockMap);
    myProcessJavaTree = xmlFormattingPolicy.processJavaTree();
  }

  public Block getOrCreateBlockFor(@NotNull Pair<PsiElement, Language> root) {
    if (!myRootToBlockMap.containsKey(root)) {
      myRootToBlockMap.put(root, createBlockFor(root));
    }
    return myRootToBlockMap.get(root);
  }

  private Block createBlockFor(final Pair<? extends PsiElement, ? extends Language> root) {
    final FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(root.getSecond(), root.getFirst());
    if (builder != null) {
      final Block result = builder.createModel(FormattingContext.create(root.getFirst(), getSettings())).getRootBlock();
      if (result instanceof XmlBlock) {
        final XmlFormattingPolicy policy = getPolicy((XmlBlock)result);
        policy.setRootModels(myRootToBlockMap);
        policy.doNotProcessJsp();
      }
      return result;
    }
    else {
      return null;
    }
  }

  protected XmlFormattingPolicy getPolicy(@NotNull XmlBlock result) {
    return result.getPolicy();
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

  /**
   * Tells how many additional blank lines must be added if there is an automatic line break before
   * tag ({@link #insertLineBreakBeforeTag(XmlTag)} returns {@code true}). Returns 0 by default (i.e. only a line break
   * will be added).
   *
   * @param xmlTag The tag to check.
   * @return The number of blank lines to insert.
   */
  public int getBlankLinesBeforeTag(XmlTag xmlTag) {
    return 0;
  }

  public @Nullable Spacing getSpacingBeforeFirstAttribute(XmlAttribute attribute) {
    return null;
  }

  public @Nullable Spacing getSpacingAfterLastAttribute(XmlAttribute attribute) {
    return null;
  }

  public abstract boolean insertLineBreakAfterTagBegin(XmlTag tag);

  public abstract boolean removeLineBreakBeforeTag(XmlTag xmlTag);

  public abstract boolean keepWhiteSpacesInsideTag(XmlTag tag);

  public abstract boolean indentChildrenOf(XmlTag parentTag);

  public abstract boolean isTextElement(XmlTag tag);

  public abstract int getTextWrap(final XmlTag tag);

  public abstract int getAttributesWrap();

  public abstract boolean getShouldAlignAttributes();

  public abstract boolean getShouldAlignText();

  public abstract boolean getShouldKeepWhiteSpaces();

  public abstract boolean getShouldAddSpaceAroundEqualityInAttribute();

  public abstract boolean getShouldAddSpaceAroundTagName();

  public abstract int getKeepBlankLines();

  public abstract boolean getShouldKeepLineBreaks();

  public abstract boolean getShouldKeepLineBreaksInText();

  public abstract boolean getKeepWhiteSpacesInsideCDATA();

  public abstract int getWhiteSpaceAroundCDATAOption();

  public Indent getTagEndIndent() { return Indent.getNoneIndent(); }

  public abstract CodeStyleSettings getSettings();

  public boolean processJsp() {
    return myProcessJsp;
  }

  public abstract boolean addSpaceIntoEmptyTag();

  public void setRootBlock(final ASTNode node, final Block rootBlock) {
    myRootToBlockMap.put(Pair.create(node.getPsi(), node.getPsi().getLanguage()), rootBlock);
  }

  public FormattingDocumentModel getDocumentModel() {
    return myDocumentModel;
  }

  public abstract boolean shouldSaveSpacesBetweenTagAndText();

  public boolean allowWrapBeforeText() {
    return true;
  }

  public boolean processJavaTree() {
    return myProcessJavaTree;
  }

  public void dontProcessJavaTree() {
    myProcessJavaTree = false;
  }

  /**
   * Inline tags are the ones which:
   * <ul>
   *   <li>Have no line breaks around them,</li>
   *   <li>Do not contain any another nested tags.</li>
   * </ul> F
   * or example:
   * <pre>
   *   &lt;para&gt;
   *     Some &lt;em&gt;emphasized&lt;/em&gt; text.
   *   &lt;/para&gt;
   * </pre>
   * If true, spaces around such tags will be preserved, no line breaks inserted.
   *
   * @return {@code false} by default.
   */
  public boolean isKeepSpacesAroundInlineTags() {
    return false;
  }
}

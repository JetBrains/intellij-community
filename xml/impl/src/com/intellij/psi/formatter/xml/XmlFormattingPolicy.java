/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public abstract class XmlFormattingPolicy {

  private Map<Pair<PsiElement, Language>, Block> myRootToBlockMap = new HashMap<>();
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
    final FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(root.getSecond(), root.getFirst());
    if (builder != null) {
      final Block result = builder.createModel(root.getFirst(), getSettings()).getRootBlock();
      if (result instanceof XmlBlock) {
        final XmlFormattingPolicy policy = getPolicy((XmlBlock)result);
        policy.setRootModels(myRootToBlockMap);
        policy.doNotProcessJsp();
      }
      return result;
    } else {
      return null;
    }
  }

  protected XmlFormattingPolicy getPolicy(final XmlBlock result) {
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
   * @param xmlTag The tag to check.
   * @return The number of blank lines to insert.
   */
  public int getBlankLinesBeforeTag(XmlTag xmlTag) {
    return 0;
  }

  @Nullable
  public Spacing getSpacingBeforeFirstAttribute(XmlAttribute attribute) {
    return null;
  }

  @Nullable
  public Spacing getSpacingAfterLastAttribute(XmlAttribute attribute) {
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
   * @return {@code false} by default.
   */
  public boolean isKeepSpacesAroundInlineTags() {
    return false;
  }
}

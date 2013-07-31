/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer.model;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.Map;

/**
 * @author Alexander Lobas
 */
public class EmptyXmlTag implements XmlTag {
  public static XmlTag INSTANCE = new EmptyXmlTag();

  @NotNull
  @Override
  public String getName() {
    return "";
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    return null;
  }

  @NotNull
  @Override
  public String getNamespace() {
    return "";
  }

  @NotNull
  @Override
  public String getLocalName() {
    return "";
  }

  @Override
  public XmlElementDescriptor getDescriptor() {
    return null;
  }

  @NotNull
  @Override
  public XmlAttribute[] getAttributes() {
    return new XmlAttribute[0];
  }

  @Override
  public XmlAttribute getAttribute(@NonNls String name, @NonNls String namespace) {
    return null;
  }

  @Override
  public XmlAttribute getAttribute(@NonNls String qname) {
    return null;
  }

  @Override
  public String getAttributeValue(@NonNls String name, @NonNls String namespace) {
    return null;
  }

  @Override
  public String getAttributeValue(@NonNls String qname) {
    return null;
  }

  @Override
  public XmlAttribute setAttribute(@NonNls String name, @NonNls String namespace, @NonNls String value) throws IncorrectOperationException {
    return null;
  }

  @Override
  public XmlAttribute setAttribute(@NonNls String qname, @NonNls String value) throws IncorrectOperationException {
    return null;
  }

  @Override
  public XmlTag createChildTag(@NonNls String localName,
                               @NonNls String namespace,
                               @Nullable @NonNls String bodyText,
                               boolean enforceNamespacesDeep) {
    return null;
  }

  @Override
  public XmlTag addSubTag(XmlTag subTag, boolean first) {
    return null;
  }

  @NotNull
  @Override
  public XmlTag[] getSubTags() {
    return new XmlTag[0];
  }

  @NotNull
  @Override
  public XmlTag[] findSubTags(@NonNls String qname) {
    return new XmlTag[0];
  }

  @NotNull
  @Override
  public XmlTag[] findSubTags(@NonNls String localName, @NonNls String namespace) {
    return new XmlTag[0];
  }

  @Override
  public XmlTag findFirstSubTag(@NonNls String qname) {
    return null;
  }

  @NotNull
  @Override
  public String getNamespacePrefix() {
    return "";
  }

  @NotNull
  @Override
  public String getNamespaceByPrefix(@NonNls String prefix) {
    return "";
  }

  @Override
  public String getPrefixByNamespace(@NonNls String namespace) {
    return null;
  }

  @Override
  public String[] knownNamespaces() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  public boolean hasNamespaceDeclarations() {
    return false;
  }

  @NotNull
  @Override
  public Map<String, String> getLocalNamespaceDeclarations() {
    return Collections.emptyMap();
  }

  @NotNull
  @Override
  public XmlTagValue getValue() {
    return new XmlTagValue() {
      @NotNull
      @Override
      public XmlTagChild[] getChildren() {
        return new XmlTagChild[0];
      }

      @NotNull
      @Override
      public XmlText[] getTextElements() {
        return new XmlText[0];
      }

      @NotNull
      @Override
      public String getText() {
        return "";
      }

      @NotNull
      @Override
      public TextRange getTextRange() {
        throw new IncorrectOperationException();
      }

      @NotNull
      @Override
      public String getTrimmedText() {
        return "";
      }

      @Override
      public void setText(String value) {
      }

      @Override
      public void setEscapedText(String value) {
      }

      @Override
      public boolean hasCDATA() {
        return false;
      }
    };
  }

  @Override
  public XmlNSDescriptor getNSDescriptor(@NonNls String namespace, boolean strict) {
    return null;
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public void collapseIfEmpty() {

  }

  @Override
  public String getSubTagText(@NonNls String qname) {
    return null;
  }

  @Override
  public XmlTag getParentTag() {
    return null;
  }

  @Override
  public XmlTagChild getNextSiblingInTag() {
    return null;
  }

  @Override
  public XmlTagChild getPrevSiblingInTag() {
    return null;
  }

  @Override
  public boolean processElements(PsiElementProcessor processor, PsiElement place) {
    return false;
  }

  @NotNull
  @Override
  public Project getProject() throws PsiInvalidElementAccessException {
    throw new IncorrectOperationException();
  }

  @NotNull
  @Override
  public Language getLanguage() {
    throw new IncorrectOperationException();
  }

  @Override
  public PsiManager getManager() {
    return null;
  }

  @NotNull
  @Override
  public PsiElement[] getChildren() {
    return new PsiElement[0];
  }

  @Override
  public PsiElement getParent() {
    return null;
  }

  @Override
  public PsiElement getFirstChild() {
    return null;
  }

  @Override
  public PsiElement getLastChild() {
    return null;
  }

  @Override
  public PsiElement getNextSibling() {
    return null;
  }

  @Override
  public PsiElement getPrevSibling() {
    return null;
  }

  @Override
  public PsiFile getContainingFile() throws PsiInvalidElementAccessException {
    return null;
  }

  @Override
  public TextRange getTextRange() {
    return null;
  }

  @Override
  public int getStartOffsetInParent() {
    return 0;
  }

  @Override
  public int getTextLength() {
    return 0;
  }

  @Override
  public PsiElement findElementAt(int offset) {
    return null;
  }

  @Override
  public PsiReference findReferenceAt(int offset) {
    return null;
  }

  @Override
  public int getTextOffset() {
    return 0;
  }

  @Override
  public String getText() {
    return null;
  }

  @NotNull
  @Override
  public char[] textToCharArray() {
    return new char[0];
  }

  @Override
  public PsiElement getNavigationElement() {
    return null;
  }

  @Override
  public PsiElement getOriginalElement() {
    return null;
  }

  @Override
  public boolean textMatches(@NotNull @NonNls CharSequence text) {
    return false;
  }

  @Override
  public boolean textMatches(@NotNull PsiElement element) {
    return false;
  }

  @Override
  public boolean textContains(char c) {
    return false;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {

  }

  @Override
  public void acceptChildren(@NotNull PsiElementVisitor visitor) {

  }

  @Override
  public PsiElement copy() {
    return null;
  }

  @Override
  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  @Override
  public PsiElement addBefore(@NotNull PsiElement element, @Nullable PsiElement anchor) throws IncorrectOperationException {
    return null;
  }

  @Override
  public PsiElement addAfter(@NotNull PsiElement element, @Nullable PsiElement anchor) throws IncorrectOperationException {
    return null;
  }

  @Override
  public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {

  }

  @Override
  public PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    return null;
  }

  @Override
  public PsiElement addRangeBefore(@NotNull PsiElement first, @NotNull PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    return null;
  }

  @Override
  public PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor) throws IncorrectOperationException {
    return null;
  }

  @Override
  public void delete() throws IncorrectOperationException {

  }

  @Override
  public void checkDelete() throws IncorrectOperationException {

  }

  @Override
  public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {

  }

  @Override
  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    return null;
  }

  @Override
  public boolean isValid() {
    return false;
  }

  @Override
  public boolean isWritable() {
    return false;
  }

  @Override
  public PsiReference getReference() {
    return null;
  }

  @NotNull
  @Override
  public PsiReference[] getReferences() {
    return new PsiReference[0];
  }

  @Override
  public <T> T getCopyableUserData(Key<T> key) {
    return null;
  }

  @Override
  public <T> void putCopyableUserData(Key<T> key, @Nullable T value) {

  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return false;
  }

  @Override
  public PsiElement getContext() {
    return null;
  }

  @Override
  public boolean isPhysical() {
    return false;
  }

  @NotNull
  @Override
  public GlobalSearchScope getResolveScope() {
    throw new IncorrectOperationException();
  }

  @NotNull
  @Override
  public SearchScope getUseScope() {
    throw new IncorrectOperationException();
  }

  @Override
  public ASTNode getNode() {
    return null;
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return false;
  }

  @Override
  public Icon getIcon(@IconFlags int flags) {
    return null;
  }

  @Override
  public PsiMetaData getMetaData() {
    return null;
  }

  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return null;
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
  }
}
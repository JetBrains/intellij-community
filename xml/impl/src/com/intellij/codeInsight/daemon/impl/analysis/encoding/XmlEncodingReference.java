/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.analysis.encoding;

import com.intellij.codeInsight.completion.simple.SimpleLookupItem;
import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * @author cdr
*/
public class XmlEncodingReference implements PsiReference, EmptyResolveMessageProvider, Comparable<XmlEncodingReference> {
  private final XmlAttributeValue myValue;

  private final String myCharsetName;
  private final TextRange myRangeInElement;
  private final int myPriority;

  public XmlEncodingReference(XmlAttributeValue value, final String charsetName, final TextRange rangeInElement, int priority) {
    myValue = value;
    myCharsetName = charsetName;
    myRangeInElement = rangeInElement;
    myPriority = priority;
  }

  public PsiElement getElement() {
    return myValue;
  }

  public TextRange getRangeInElement() {
    return myRangeInElement;
  }

  @Nullable
  public PsiElement resolve() {
    return CharsetToolkit.forName(myCharsetName) == null ? null : myValue;
    //if (ApplicationManager.getApplication().isUnitTestMode()) return myValue; // tests do not have full JDK
    //String fqn = charset.getClass().getName();
    //return myValue.getManager().findClass(fqn, GlobalSearchScope.allScope(myValue.getProject()));
  }

  public String getUnresolvedMessagePattern() {
    return XmlErrorMessages.message("unknown.encoding.0");
  }

  @NotNull
  public String getCanonicalText() {
    return myCharsetName;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return null;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  public boolean isReferenceTo(PsiElement element) {
    return false;
  }

  @NotNull
  public Object[] getVariants() {
    Charset[] charsets = CharsetToolkit.getAvailableCharsets();
    List<LookupElement> suggestions = new ArrayList<LookupElement>(charsets.length);
    for (Charset charset : charsets) {
      suggestions.add(LookupElementBuilder.create(charset.name()).setCaseSensitive(false));
    }
    return suggestions.toArray(new LookupElement[suggestions.size()]);
  }

  public boolean isSoft() {
    return false;
  }

  public int compareTo(XmlEncodingReference ref) {
    return myPriority - ref.myPriority;
  }
}

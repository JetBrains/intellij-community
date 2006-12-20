/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author peter
*/
class JavaClassReferenceSet {
  private static final char SEPARATOR = '.';
  protected static final char SEPARATOR2 = '$';

  private PsiReference[] myReferences;
  private PsiElement myElement;
  private final int myStartInElement;
  private final ReferenceType myType;
  private final JavaClassReferenceProvider myProvider;

  JavaClassReferenceSet(String str, PsiElement element, int startInElement, ReferenceType type, final boolean isStatic, JavaClassReferenceProvider provider) {
    myType = type;
    myStartInElement = startInElement;
    myProvider = provider;
    reparse(str, element, isStatic);
  }

  public JavaClassReferenceProvider getProvider() {
    return myProvider;
  }

  private void reparse(String str, PsiElement element, final boolean isStaticImport) {
    myElement = element;
    final List<JavaClassReference> referencesList = new ArrayList<JavaClassReference>();
    int currentDot = -1;
    int index = 0;
    boolean allowDollarInNames = isAllowDollarInNames(element);
    boolean parsingClassNames = true;

    while (parsingClassNames) {
      int nextDotOrDollar = str.indexOf(SEPARATOR, currentDot + 1);
      if (nextDotOrDollar == -1 && allowDollarInNames) {
        nextDotOrDollar = str.indexOf(SEPARATOR2, currentDot + 1);
      }

      if (nextDotOrDollar == -1) {
        nextDotOrDollar = currentDot + 1;
        for(int i = nextDotOrDollar; i < str.length() && Character.isJavaIdentifierPart(str.charAt(i)); ++i) nextDotOrDollar++;
        parsingClassNames = false;
        int j = nextDotOrDollar;
        while(j < str.length() && Character.isWhitespace(str.charAt(j))) ++j;

        if (j < str.length()) {
          char ch = str.charAt(j);
          boolean recognized = false;

          if (ch == '[') {
            j++;
            while(j < str.length() && Character.isWhitespace(str.charAt(j))) ++j;

            if (j < str.length()) {
              ch = str.charAt(j);

              if (ch == ']') {
                j++;
                while(j < str.length() && Character.isWhitespace(str.charAt(j))) ++j;

                recognized = j == str.length();
              }
            }
          }

          final Boolean aBoolean = JavaClassReferenceProvider.JVM_FORMAT.getValue(getOptions());
          if (aBoolean == null || !aBoolean) {
            if (!recognized) nextDotOrDollar = -1; // nonsensible characters anyway, don't do resolve
          }
        }
      }

      final String subreferenceText =
        nextDotOrDollar > 0 ? str.substring(currentDot + 1, nextDotOrDollar) : str.substring(currentDot + 1);

      TextRange textRange =
        new TextRange(myStartInElement + currentDot + 1, myStartInElement + (nextDotOrDollar > 0 ? nextDotOrDollar : str.length()));
      JavaClassReference currentContextRef = new JavaClassReference(this, textRange, index++, subreferenceText, isStaticImport);
      referencesList.add(currentContextRef);
      if ((currentDot = nextDotOrDollar) < 0) {
        break;
      }
    }

    myReferences = referencesList.toArray(new JavaClassReference[referencesList.size()]);
  }

  protected boolean isAllowDollarInNames(final PsiElement element) {
    return element.getLanguage() instanceof XMLLanguage;
  }

  void reparse(PsiElement element, final TextRange range) {
    final String text = element.getText().substring(range.getStartOffset(), range.getEndOffset());

    //if (element instanceof XmlAttributeValue) {
    //  text = StringUtil.stripQuotesAroundValue(element.getText().substring(range.getStartOffset(), range.getEndOffset()));
    //}
    //else if (element instanceof XmlTag) {
    //  text = ((XmlTag)element).getValue().getTrimmedText();
    //}
    //else {
    //  text = element.getText();
    //}
    reparse(text, element, false);
  }

  protected PsiReference getReference(int index) {
    return myReferences[index];
  }

  protected PsiReference[] getAllReferences() {
    return myReferences;
  }

  ReferenceType getType(int index) {
    if (index != myReferences.length - 1) {
      return new ReferenceType(ReferenceType.JAVA_CLASS, ReferenceType.JAVA_PACKAGE);
    }
    return myType;
  }

  protected boolean isSoft() {
    return myProvider.isSoft();
  }

  public PsiElement getElement() {
    return myElement;
  }

  PsiReference[] getReferences() {
    return myReferences;
  }

  @Nullable
  public Map<CustomizableReferenceProvider.CustomizationKey, Object> getOptions() {
    return myProvider.getOptions();
  }
}

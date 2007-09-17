/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author peter
*/
public class JavaClassReferenceSet {
  public static final char SEPARATOR = '.';
  public static final char SEPARATOR2 = '$';
  private static final char SEPARATOR3 = '<';
  private static final char SEPARATOR4 = ',';
  private static final ReferenceType PACKAGE_OR_CLASS_TYPE = new ReferenceType(ReferenceType.JAVA_CLASS, ReferenceType.JAVA_PACKAGE);
  private static final ReferenceType CLASS_TYPE = new ReferenceType(ReferenceType.JAVA_CLASS);

  private PsiReference[] myReferences;
  private List<JavaClassReferenceSet> myNestedGenericParameterReferences;
  private JavaClassReferenceSet myContext;
  private PsiElement myElement;
  private final int myStartInElement;
  private final JavaClassReferenceProvider myProvider;

  public JavaClassReferenceSet(String str, PsiElement element, int startInElement, final boolean isStatic, JavaClassReferenceProvider provider) {
    this(str, element, startInElement, isStatic, provider, null);
  }

  private JavaClassReferenceSet(String str, PsiElement element, int startInElement, final boolean isStatic, JavaClassReferenceProvider provider,
                        JavaClassReferenceSet context) {
    myStartInElement = startInElement;
    myProvider = provider;
    reparse(str, element, isStatic, context);
  }

  public JavaClassReferenceProvider getProvider() {
    return myProvider;
  }

  private void reparse(String str, PsiElement element, final boolean isStaticImport, JavaClassReferenceSet context) {
    myElement = element;
    myContext = context;
    final List<JavaClassReference> referencesList = new ArrayList<JavaClassReference>();
    int currentDot = -1;
    int referenceIndex = 0;
    boolean allowDollarInNames = isAllowDollarInNames();
    boolean allowGenerics = false;
    boolean allowGenericsCalculated = false;
    boolean parsingClassNames = true;

    while (parsingClassNames) {
      int nextDotOrDollar = -1;
      for(int curIndex = currentDot + 1; curIndex < str.length(); ++curIndex) {
        final char ch = str.charAt(curIndex);

        if (ch == SEPARATOR ||
            (ch == SEPARATOR2 && allowDollarInNames)
           ) {
          nextDotOrDollar = curIndex;
          break;
        }
        
        if (((ch == SEPARATOR3 || ch == SEPARATOR4))) {
          if (!allowGenericsCalculated) {
            allowGenerics = !isStaticImport && PsiUtil.getLanguageLevel(element).hasEnumKeywordAndAutoboxing();
            allowGenericsCalculated = true;
          }

          if (allowGenerics) {
            nextDotOrDollar = curIndex;
            break;
          }
        }
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
          if (aBoolean == null || !aBoolean.booleanValue()) {
            if (!recognized) nextDotOrDollar = -1; // nonsensible characters anyway, don't do resolve
          }
        }
      }

      if (nextDotOrDollar != -1 && nextDotOrDollar < str.length()) {
        final char c = str.charAt(nextDotOrDollar);
        if (c == SEPARATOR3) {
          int end = str.lastIndexOf('>');
          if (end != -1 && end > nextDotOrDollar) {
            if (myNestedGenericParameterReferences == null) myNestedGenericParameterReferences = new ArrayList<JavaClassReferenceSet>(1);
            myNestedGenericParameterReferences.add(
              new JavaClassReferenceSet(
                str.substring(nextDotOrDollar + 1, end),
                myElement,
                myStartInElement + nextDotOrDollar + 1,
                isStaticImport,
                myProvider,
                this
              )
            );
            parsingClassNames = false;
          } else {
            nextDotOrDollar = -1; // nonsensible characters anyway, don't do resolve
          }
        } else if (SEPARATOR4 == c && myContext != null) {
          if (myContext.myNestedGenericParameterReferences == null) myContext.myNestedGenericParameterReferences = new ArrayList<JavaClassReferenceSet>(1);
          myContext.myNestedGenericParameterReferences.add(
            new JavaClassReferenceSet(
              str.substring(nextDotOrDollar + 1),
              myElement,
              myStartInElement + nextDotOrDollar + 1,
              isStaticImport,
              myProvider,
              this
            )
          );
          parsingClassNames = false;
        }
      }

      final String subreferenceText =
        nextDotOrDollar > 0 ? str.substring(currentDot + 1, nextDotOrDollar) : str.substring(currentDot + 1);

      TextRange textRange =
        new TextRange(myStartInElement + currentDot + 1, myStartInElement + (nextDotOrDollar > 0 ? nextDotOrDollar : str.length()));
      JavaClassReference currentContextRef = createReference(referenceIndex, subreferenceText, textRange, isStaticImport);
      referenceIndex++;
      referencesList.add(currentContextRef);
      if ((currentDot = nextDotOrDollar) < 0) {
        break;
      } 
    }

    myReferences = referencesList.toArray(new JavaClassReference[referencesList.size()]);
  }

  protected JavaClassReference createReference(final int referenceIndex, final String subreferenceText, final TextRange textRange,
                                               final boolean staticImport) {
    return new JavaClassReference(this, textRange, referenceIndex, subreferenceText, staticImport);
  }

  public boolean isAllowDollarInNames() {
    return myElement.getLanguage() instanceof XMLLanguage;
  }

  public void reparse(PsiElement element, final TextRange range) {
    final String text = element.getText().substring(range.getStartOffset(), range.getEndOffset());
    reparse(text, element, false, myContext);
  }

  public PsiReference getReference(int index) {
    return myReferences[index];
  }

  public PsiReference[] getAllReferences() {
    PsiReference[] result = myReferences;
    if (myNestedGenericParameterReferences != null) {
      for(JavaClassReferenceSet set:myNestedGenericParameterReferences) {
        result = ArrayUtil.mergeArrays(result, set.getAllReferences(),PsiReference.class);
      }
    }
    return result;
  }

  public ReferenceType getType(int index) {
    if (index != myReferences.length - 1) {
      return PACKAGE_OR_CLASS_TYPE;
    }
    return CLASS_TYPE;
  }

  public boolean isSoft() {
    return myProvider.isSoft();
  }

  public PsiElement getElement() {
    return myElement;
  }

  public PsiReference[] getReferences() {
    return myReferences;
  }

  @Nullable
  public Map<CustomizableReferenceProvider.CustomizationKey, Object> getOptions() {
    return myProvider.getOptions();
  }
}

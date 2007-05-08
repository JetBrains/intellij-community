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
  private static final char SEPARATOR = '.';
  protected static final char SEPARATOR2 = '$';

  private PsiReference[] myReferences;
  private List<JavaClassReferenceSet> myNestedGenericParameterReferences;
  private JavaClassReferenceSet myContext;
  private PsiElement myElement;
  private final int myStartInElement;
  private final ReferenceType myType;
  private final JavaClassReferenceProvider myProvider;
  private static final char SEPARATOR3 = '<';
  private static final char SEPARATOR4 = ',';

  public JavaClassReferenceSet(String str, PsiElement element, int startInElement, ReferenceType type, final boolean isStatic, JavaClassReferenceProvider provider) {
    this(str, element, startInElement, type, isStatic, provider, null);
  }

  public JavaClassReferenceSet(String str, PsiElement element, int startInElement, ReferenceType type, final boolean isStatic, JavaClassReferenceProvider provider,
                        JavaClassReferenceSet context) {
    myType = type;
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
          if (aBoolean == null || !aBoolean) {
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
                myType,
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
              myType,
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
      JavaClassReference currentContextRef = new JavaClassReference(this, textRange, referenceIndex++, subreferenceText, isStaticImport);
      referencesList.add(currentContextRef);
      if ((currentDot = nextDotOrDollar) < 0) {
        break;
      } 
    }

    myReferences = referencesList.toArray(new JavaClassReference[referencesList.size()]);
  }

  protected boolean isAllowDollarInNames() {
    return myElement.getLanguage() instanceof XMLLanguage;
  }

  public void reparse(PsiElement element, final TextRange range) {
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
    reparse(text, element, false, myContext);
  }

  protected PsiReference getReference(int index) {
    return myReferences[index];
  }

  protected PsiReference[] getAllReferences() {
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

  public PsiReference[] getReferences() {
    return myReferences;
  }

  @Nullable
  public Map<CustomizableReferenceProvider.CustomizationKey, Object> getOptions() {
    return myProvider.getOptions();
  }
}

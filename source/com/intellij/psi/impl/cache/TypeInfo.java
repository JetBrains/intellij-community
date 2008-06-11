package com.intellij.psi.impl.cache;

import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.impl.compiled.ClsTypeElementImpl;
import com.intellij.util.io.StringRef;

/**
 * @author max
 */
public class TypeInfo {
  public final StringRef text;
  public final byte arrayCount;
  public final boolean isEllipsis;


  public TypeInfo(StringRef text, byte arrayCount, boolean ellipsis) {
    this.text = text;
    this.arrayCount = arrayCount;
    isEllipsis = ellipsis;
  }

  public static TypeInfo create(PsiType type, PsiTypeElement typeElement) {
    if (type == null) return null;

    final boolean isEllipsis = type instanceof PsiEllipsisType;
    int arrayCount = type.getArrayDimensions();


    final String text;
    if (typeElement != null) {
      while (typeElement.getFirstChild() instanceof PsiTypeElement) {
        typeElement = (PsiTypeElement)typeElement.getFirstChild();
      }

      text = typeElement instanceof PsiCompiledElement
             ? ((ClsTypeElementImpl)typeElement).getCanonicalText()
             : typeElement.getText();
    }
    else {
      type = type.getDeepComponentType();
      text = type.getInternalCanonicalText();
    }

    return new TypeInfo(StringRef.fromString(text), (byte)arrayCount, isEllipsis);
  }

  public static TypeInfo fromString(String typeText, boolean isEllipsis) {
    assert !typeText.endsWith("...") : typeText;

    byte arrayCount = 0;
    while (typeText.endsWith("[]")) {
      arrayCount++;
      typeText = typeText.substring(0, typeText.length() - 2);
    }

    StringRef text = StringRef.fromString(typeText);

    return new TypeInfo(text, arrayCount, isEllipsis);
  }
  public static TypeInfo fromString(String typeText) {
    boolean isEllipsis = false;
    if (typeText.endsWith("...")) {
      isEllipsis = true;
      typeText = typeText.substring(0, typeText.length() - 3);
    }

    byte arrayCount = 0;
    while (typeText.endsWith("[]")) {
      arrayCount++;
      typeText = typeText.substring(0, typeText.length() - 2);
    }

    StringRef text = StringRef.fromString(typeText);

    return new TypeInfo(text, arrayCount, isEllipsis);
  }
}

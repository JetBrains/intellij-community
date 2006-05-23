package com.intellij.psi.impl.source;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 *  @author dsl
 */
public class PsiImmediateClassType extends PsiClassType {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiImmediateClassType");
  private final PsiClass myClass;
  private final PsiSubstitutor mySubstitutor;
  private final PsiManager myManager;
  private String myCanonicalText;
  private String myPresentableText;
  private String myInternalCanonicalText;

  private final PsiClassType.ClassResolveResult myClassResolveResult = new PsiClassType.ClassResolveResult() {
    public PsiClass getElement() {
      return myClass;
    }

    public PsiSubstitutor getSubstitutor() {
      return mySubstitutor;
    }

    public boolean isValidResult() {
      return true;
    }

    public boolean isAccessible() {
      return true;
    }

    public boolean isStaticsScopeCorrect() {
      return true;
    }

    public PsiElement getCurrentFileResolveScope() {
      return null;
    }

    public boolean hasCandidates() {
      return true;
    }

    public boolean isPackagePrefixPackageReference() {
      return false;
    }
  };

  public PsiImmediateClassType(PsiClass aClass, PsiSubstitutor substitutor) {
    this (aClass, substitutor, null);
  }

  public PsiImmediateClassType(final PsiClass aClass, final PsiSubstitutor substitutor, final LanguageLevel languageLevel) {
    myClass = aClass;
    myManager = aClass.getManager();
    mySubstitutor = substitutor;
    LOG.assertTrue(mySubstitutor != null);
    myLanguageLevel = languageLevel;
  }

  public PsiClass resolve() {
    return myClass;
  }

  public String getClassName() {
    return myClass.getName();
  }
  @NotNull
  public PsiType[] getParameters() {
    List<PsiType> lst = new ArrayList<PsiType>();
    final PsiTypeParameter[] parameters = myClass.getTypeParameters();
    for (PsiTypeParameter parameter : parameters) {
      lst.add(mySubstitutor.substitute(parameter));
    }
    return lst.toArray(PsiType.EMPTY_ARRAY);
  }

  @NotNull
  public PsiClassType.ClassResolveResult resolveGenerics() {
    return myClassResolveResult;
  }

  @NotNull
  public PsiClassType rawType() {
    return myClass.getManager().getElementFactory().createType(myClass);
  }

  public String getPresentableText() {
    if (myPresentableText == null) {
      final StringBuffer buffer = new StringBuffer();
      buildText(myClass, buffer, false, false);
      myPresentableText = buffer.toString();
    }
    return myPresentableText;
  }

  public String getCanonicalText() {
    if (myCanonicalText == null) {
      final StringBuffer buffer = new StringBuffer();
      buildText(myClass, buffer, true, false);
      myCanonicalText = buffer.toString();
    }
    return myCanonicalText;
  }

  public String getInternalCanonicalText() {
    if (myInternalCanonicalText == null) {
      final StringBuffer buffer = new StringBuffer();
      buildText(myClass, buffer, true, true);
      myInternalCanonicalText = buffer.toString();
    }
    return myInternalCanonicalText;
  }

  private void buildText(PsiClass aClass, StringBuffer buffer, boolean canonical, boolean internal) {
    PsiSubstitutor substitutor = mySubstitutor;
    if (aClass instanceof PsiAnonymousClass) {
      ClassResolveResult baseResolveResult = ((PsiAnonymousClass) aClass).getBaseClassType().resolveGenerics();
      aClass = baseResolveResult.getElement();
      substitutor = baseResolveResult.getSubstitutor();
      if (aClass == null) return;
    }
    PsiClass parentClass = null;
    if (!aClass.hasModifierProperty(PsiModifier.STATIC)) {
      final PsiElement parent = aClass.getParent();
      if (parent instanceof PsiClass) {
        parentClass = (PsiClass)parent;
      }
    }

    if (parentClass != null) {
      buildText(parentClass, buffer, canonical, false);
      buffer.append('.');
      buffer.append(aClass.getName());
    }
    else {
      final String name;
      if (!canonical) {
        name = aClass.getName();
      }
      else {
        final String qualifiedName = aClass.getQualifiedName();
        if (qualifiedName != null) {
          name = qualifiedName;
        }
        else {
          name = aClass.getName();
        }
      }
      buffer.append(name);
    }

    final PsiTypeParameter[] typeParameters = aClass.getTypeParameters();
    if (typeParameters.length > 0) {
      StringBuffer pineBuffer = new StringBuffer();
      pineBuffer.append('<');
      for (int i = 0; i < typeParameters.length; i++) {
        PsiTypeParameter typeParameter = typeParameters[i];
        if (i > 0) pineBuffer.append(',');
        final PsiType substitutionResult = substitutor.substitute(typeParameter);
        if (substitutionResult == null) {
          pineBuffer = null;
          break;
        }
        if (!canonical) {
          pineBuffer.append(substitutionResult.getPresentableText());
        }
        else {
          if (internal) {
            pineBuffer.append(substitutionResult.getInternalCanonicalText());
          }
          else {
            pineBuffer.append(substitutionResult.getCanonicalText());
          }
        }
      }
      if (pineBuffer != null) {
        buffer.append(pineBuffer);
        buffer.append('>');
      }
    }
  }

  public boolean isValid() {
    return myClass.isValid() && mySubstitutor.isValid();
  }

  public boolean equalsToText(String text) {
    PsiElementFactory factory = myManager.getElementFactory();
    final PsiType patternType;
    try {
      patternType = factory.createTypeFromText(text, null);
    }
    catch (IncorrectOperationException e) {
      return false;
    }
    return equals(patternType);

  }

  @NotNull
  public GlobalSearchScope getResolveScope() {
    return myClass.getResolveScope();
  }

  @NotNull
  public LanguageLevel getLanguageLevel() {
    if (myLanguageLevel != null) return myLanguageLevel;
    return PsiUtil.getLanguageLevel(myClass);
  }

  public PsiClassType setLanguageLevel(final LanguageLevel languageLevel) {
    if (languageLevel.equals(myLanguageLevel)) return this;
    return new PsiImmediateClassType(myClass, mySubstitutor, languageLevel);
  }
}

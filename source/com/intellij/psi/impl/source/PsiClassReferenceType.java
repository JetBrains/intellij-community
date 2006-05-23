package com.intellij.psi.impl.source;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.impl.light.LightClassReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class PsiClassReferenceType extends PsiClassType {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiClassReferenceType");
  private final PsiJavaCodeReferenceElement myReference;

  public PsiClassReferenceType(PsiJavaCodeReferenceElement reference) {
    LOG.assertTrue(reference != null);
    myReference = reference;
  }

  public boolean isValid() {
    return myReference.isValid();
  }

  public boolean equalsToText(String text) {
    return Comparing.equal(text, getCanonicalText());
  }

  @NotNull
  public GlobalSearchScope getResolveScope() {
    return myReference.getResolveScope();
  }

  @NotNull
  public LanguageLevel getLanguageLevel() {
    if (myLanguageLevel != null) return myLanguageLevel;
    return PsiUtil.getLanguageLevel(myReference);
  }

  public PsiClassType setLanguageLevel(final LanguageLevel languageLevel) {
    if (languageLevel.equals(myLanguageLevel)) return this;
    final PsiClassReferenceType copy = new PsiClassReferenceType(myReference);
    copy.myLanguageLevel = languageLevel;
    return copy;
  }

  public PsiClass resolve() {
    return resolveGenerics().getElement();
  }

  private static class DelegatingClassResolveResult implements ClassResolveResult {
    private final JavaResolveResult myDelegate;

    private DelegatingClassResolveResult(JavaResolveResult delegate) {
      myDelegate = delegate;
    }

    public boolean hasCandidates() {
      return myDelegate.getElement() != null;
    }

    public PsiSubstitutor getSubstitutor() {
      return myDelegate.getSubstitutor();
    }

    public boolean isValidResult() {
      return myDelegate.isValidResult();
    }

    public boolean isAccessible() {
      return myDelegate.isAccessible();
    }

    public boolean isStaticsScopeCorrect() {
      return myDelegate.isStaticsScopeCorrect();
    }

    public PsiElement getCurrentFileResolveScope() {
      return myDelegate.getCurrentFileResolveScope();
    }

    public boolean isPackagePrefixPackageReference() {
          return myDelegate.isPackagePrefixPackageReference();
      }

    public PsiClass getElement() {
      final PsiElement element = myDelegate.getElement();
      return element instanceof PsiClass ? (PsiClass)element : null;
    }
  }

  private static ClassResolveResult createClassResolveResult(JavaResolveResult result) {
    return new DelegatingClassResolveResult(result);
  }

  @NotNull
  public ClassResolveResult resolveGenerics() {
    final JavaResolveResult result = myReference.advancedResolve(false);
    return createClassResolveResult(result);
  }

  @NotNull
  public PsiClassType rawType() {
    PsiElement resolved = myReference.resolve();
    PsiManager manager = myReference.getManager();
    final PsiElementFactory factory = manager.getElementFactory();
    if (resolved instanceof PsiClass) {
      final PsiSubstitutor rawSubstitutor = factory.createRawSubstitutor((PsiClass) resolved);
      return factory.createType((PsiClass) resolved, rawSubstitutor, getLanguageLevel());
    }
    String qualifiedName = myReference.getQualifiedName();
    return new PsiClassReferenceType(new LightClassReference(manager, myReference.getReferenceName(), qualifiedName, myReference.getResolveScope()));
  }

  public String getClassName() {
    return myReference.getReferenceName();
  }

  @NotNull
  public PsiType[] getParameters() {
    return myReference.getTypeParameters();
  }

  public PsiClassType createImmediateCopy() {
    final PsiClassType.ClassResolveResult resolveResult = resolveGenerics();
    if (resolveResult.getElement() == null) return this;
    return new PsiImmediateClassType(resolveResult.getElement(), resolveResult.getSubstitutor());
  }

  public String getPresentableText() {
    return PsiNameHelper.getPresentableText(myReference);
  }

  public String getCanonicalText() {
    return myReference.getCanonicalText();
  }

  public String getInternalCanonicalText() {
    return getCanonicalText();
  }

  public PsiJavaCodeReferenceElement getReference() {
    return myReference;
  }
}

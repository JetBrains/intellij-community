package com.intellij.psi.impl.source;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightClassReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;

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
    PsiElementFactory factory = myReference.getManager().getElementFactory();
    PsiType patternType;
    try {
      patternType = factory.createTypeFromText(text, null);
    }
    catch (IncorrectOperationException e) {
      return false;
    }
    return equals(patternType);
  }

  public GlobalSearchScope getResolveScope() {
    return myReference.getResolveScope();
  }

  public PsiClass resolve() {
    ClassResolveResult result = resolveGenerics();
    if (result != null) {
      return result.getElement();
    }
    else {
      return null;
    }
  }

  private static class DelegatingClassResolveResult implements ClassResolveResult {
    private final ResolveResult myDelegate;

    private DelegatingClassResolveResult(ResolveResult delegate) {
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

  private static ClassResolveResult createClassResolveResult(ResolveResult result) {
    return new DelegatingClassResolveResult(result);
  }

  public ClassResolveResult resolveGenerics() {
    final ResolveResult result = myReference.advancedResolve(false);
    return createClassResolveResult(result);
  }

  public PsiClassType rawType() {
    PsiElement resolved = myReference.resolve();
    PsiManager manager = myReference.getManager();
    final PsiElementFactory factory = manager.getElementFactory();
    if (resolved instanceof PsiClass) {
      final PsiSubstitutor rawSubstitutor = factory.createRawSubstitutor((PsiClass) resolved);
      return factory.createType((PsiClass) resolved, rawSubstitutor);
    }
    String qualifiedName = myReference.getQualifiedName();
    return new PsiClassReferenceType(new LightClassReference(manager, myReference.getReferenceName(), qualifiedName, myReference.getResolveScope()));
  }

  public String getClassName() {
    return myReference.getReferenceName();
  }

  public PsiClassType getQualiferType() {
    if (!(myReference instanceof SourceJavaCodeReference)) {
      // todo[dsl] fix this for compiled code
      return null;
    }
    final PsiElement psiElement = SourceTreeToPsiMap.treeElementToPsi(((SourceJavaCodeReference) myReference).getTreeQualifier());
    if (psiElement instanceof PsiJavaCodeReferenceElement) {
      final PsiJavaCodeReferenceElement qualifierReference = ((PsiJavaCodeReferenceElement)psiElement);
      if (qualifierReference.getTypeParameters().length > 0) {
        return new PsiClassReferenceType(qualifierReference);
      }
      else {
        return null;
      }
    }
    else {
      return null;
    }
  }

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

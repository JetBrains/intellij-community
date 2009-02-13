package com.intellij.lang.properties.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * @author cdr
 */
public class PropertyValueImpl extends LeafPsiElement {
  private static final Constructor<?> ourJavaClassListReferenceProviderConstructor;
  private static final Method ourJavaClassListReferenceProviderGetReferencesMethod;

  static {  // TODO: make java spi
    Constructor<?> javaClassListReferenceProviderConstructor = null;
    Method javaClassListReferenceProviderGetReferencesMethod = null;
    try {
      Class c = Class.forName("com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassListReferenceProvider");
      javaClassListReferenceProviderConstructor = c.getConstructor(Project.class);
      javaClassListReferenceProviderGetReferencesMethod = c.getMethod("getReferencesByString",String.class, PsiElement.class, int.class);
    } catch (Exception ex) {}
    finally {
      ourJavaClassListReferenceProviderConstructor = javaClassListReferenceProviderConstructor;
      ourJavaClassListReferenceProviderGetReferencesMethod = javaClassListReferenceProviderGetReferencesMethod;
    }
  }

  public PropertyValueImpl(IElementType type, CharSequence text) {
    super(type, text);
  }

  @NotNull
  public PsiReference[] getReferences() {
    if (ourJavaClassListReferenceProviderConstructor != null) {
      String text = getText();
      String[] words = text.split("\\s");
      if (words.length == 0) return PsiReference.EMPTY_ARRAY;
      try {
        GenericReferenceProvider referenceProvider = (GenericReferenceProvider)ourJavaClassListReferenceProviderConstructor.newInstance(getProject());
        referenceProvider.setSoft(true);
        return (PsiReference[])ourJavaClassListReferenceProviderGetReferencesMethod.invoke(referenceProvider, words[0], this, 0);
      } catch (Throwable ex) {}
    }

    return PsiReference.EMPTY_ARRAY;
  }

  public PsiReference getReference() {
    PsiReference[] references = getReferences();
    return references.length == 0 ? null : references[0];
  }

  @NonNls
  public String toString() {
    return "Property value: " + getText();
  }
}

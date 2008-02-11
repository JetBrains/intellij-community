package com.intellij.psi.impl.source;

import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;

public class JavaDummyHolder extends DummyHolder implements PsiImportHolder {
  private final LinkedHashMap<String, PsiClass> myPseudoImports = new LinkedHashMap<String, PsiClass>();

  public JavaDummyHolder(@NotNull PsiManager manager, TreeElement contentElement, PsiElement context) {
    super(manager, contentElement, context);
    setLanguage(StdLanguages.JAVA);
  }

  public JavaDummyHolder(@NotNull PsiManager manager, CharTable table, boolean validity) {
    super(manager, table, validity);
    setLanguage(StdLanguages.JAVA);
  }

  public JavaDummyHolder(@NotNull PsiManager manager, PsiElement context) {
    super(manager, context);
    setLanguage(StdLanguages.JAVA);
  }

  public JavaDummyHolder(@NotNull PsiManager manager, TreeElement contentElement, PsiElement context, CharTable table) {
    super(manager, contentElement, context, table);
    setLanguage(StdLanguages.JAVA);
  }

  public JavaDummyHolder(@NotNull PsiManager manager, PsiElement context, CharTable table) {
    super(manager, context, table);
    setLanguage(StdLanguages.JAVA);
  }

  public JavaDummyHolder(@NotNull PsiManager manager, final CharTable table, final Language language) {
    super(manager, table, language);
    setLanguage(StdLanguages.JAVA);
  }

  public boolean importClass(PsiClass aClass) {
    if (myContext != null) {
      final PsiClass resolved = JavaPsiFacade.getInstance(getProject()).getResolveHelper().resolveReferencedClass(aClass.getName(), myContext);
      if (resolved != null) {
        return getManager().areElementsEquivalent(aClass, resolved);
      }
    }

    String className = aClass.getName();
    if (!myPseudoImports.containsKey(className)) {
      myPseudoImports.put(className, aClass);
      myManager.nonPhysicalChange(); // to clear resolve caches!
      return true;
    }
    else {
      return true;
    }
  }

  public boolean hasImports() {
    return !myPseudoImports.isEmpty();
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    ElementClassHint classHint = processor.getHint(ElementClassHint.class);
    if (classHint == null || classHint.shouldProcess(PsiClass.class)) {
      final NameHint nameHint = processor.getHint(NameHint.class);
      final String name = nameHint != null ? nameHint.getName() : null;
      //"pseudo-imports"
      if (name != null) {
        PsiClass imported = myPseudoImports.get(name);
        if (imported != null) {
          if (!processor.execute(imported, state)) return false;
        }
      } else {
        for (PsiClass aClass : myPseudoImports.values()) {
          if (!processor.execute(aClass, state)) return false;
        }
      }

      if (myContext == null) {
        if (!JavaResolveUtil.processImplicitlyImportedPackages(processor, state, place, getManager())) return false;
      }
    }
    return true;
  }

  public boolean isSamePackage(PsiElement other) {
    if (other instanceof DummyHolder) {
      final PsiElement otherContext = ((DummyHolder)other).myContext;
      if (myContext == null) return otherContext == null;
      return JavaPsiFacade.getInstance(myContext.getProject()).arePackagesTheSame(myContext, otherContext);
    }
    if (other instanceof PsiJavaFile) {
      if (myContext != null) return JavaPsiFacade.getInstance(myContext.getProject()).arePackagesTheSame(myContext, other);
      final String packageName = ((PsiJavaFile)other).getPackageName();
      return "".equals(packageName);
    }
    return false;
  }

  public boolean isInPackage(PsiPackage aPackage) {
    if (myContext != null) return JavaPsiFacade.getInstance(myContext.getProject()).isInPackage(myContext, aPackage);
    if (aPackage == null) return true;
    return "".equals(aPackage.getQualifiedName());
  }


  public void setOriginalFile(final PsiFile originalFile) {
    super.setOriginalFile(originalFile);
    putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, PsiUtil.getLanguageLevel(originalFile));
  }
}

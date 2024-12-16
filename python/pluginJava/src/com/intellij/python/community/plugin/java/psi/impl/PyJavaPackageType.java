// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.java.psi.impl;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.impl.ResolveResultList;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;


public class PyJavaPackageType implements PyType {
  private final PsiPackage myPackage;
  @Nullable private final Module myModule;

  public PyJavaPackageType(PsiPackage aPackage, @Nullable Module module) {
    myPackage = aPackage;
    myModule = module;
  }

  @Override
  public List<? extends RatedResolveResult> resolveMember(@NotNull String name,
                                                          @Nullable PyExpression location,
                                                          @NotNull AccessDirection direction,
                                                          @NotNull PyResolveContext resolveContext) {
    Project project = myPackage.getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    String childName = myPackage.getQualifiedName() + "." + name;
    GlobalSearchScope scope = getScope(project);
    ResolveResultList result = new ResolveResultList();
    final PsiClass[] classes = facade.findClasses(childName, scope);
    for (PsiClass aClass : classes) {
      result.poke(aClass, RatedResolveResult.RATE_NORMAL);
    }
    final PsiPackage psiPackage = facade.findPackage(childName);
    if (psiPackage != null) {
      result.poke(psiPackage, RatedResolveResult.RATE_NORMAL);
    }
    return result;
  }

  private GlobalSearchScope getScope(Project project) {
    return myModule != null ? myModule.getModuleWithDependenciesAndLibrariesScope(false) : ProjectScope.getAllScope(project);
  }

  @Override
  public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
    List<Object> variants = new ArrayList<>();
    final GlobalSearchScope scope = getScope(location.getProject());
    final PsiClass[] classes = myPackage.getClasses(scope);
    for (PsiClass psiClass : classes) {
      variants.add(LookupElementBuilder.create(psiClass).withIcon(psiClass.getIcon(0)));
    }
    final PsiPackage[] subPackages = myPackage.getSubPackages(scope);
    for (PsiPackage subPackage : subPackages) {
      variants.add(LookupElementBuilder.create(subPackage).withIcon(subPackage.getIcon(0)));
    }
    return ArrayUtil.toObjectArray(variants);
  }

  @Override
  public String getName() {
    return myPackage.getQualifiedName();
  }

  @Override
  public boolean isBuiltin() {
    return false;
  }

  @Override
  public void assertValid(String message) {
  }
}

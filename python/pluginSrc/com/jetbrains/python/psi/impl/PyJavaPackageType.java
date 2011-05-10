package com.jetbrains.python.psi.impl;

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
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PyJavaPackageType implements PyType {
  private final PsiPackage myPackage;
  @Nullable private final Module myModule;

  public PyJavaPackageType(PsiPackage aPackage, @Nullable Module module) {
    myPackage = aPackage;
    myModule = module;
  }

  @Override
  public List<? extends PsiElement> resolveMember(String name,
                                                  @Nullable PyExpression location,
                                                  AccessDirection direction,
                                                  PyResolveContext resolveContext) {
    Project project = myPackage.getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    String childName = myPackage.getQualifiedName() + "." + name;
    GlobalSearchScope scope = getScope(project);
    List<PsiElement> result = new ArrayList<PsiElement>();
    Collections.addAll(result, facade.findClasses(childName, scope));
    final PsiPackage psiPackage = facade.findPackage(childName);
    if (psiPackage != null) {
      result.add(psiPackage);
    }
    return result;
  }

  private GlobalSearchScope getScope(Project project) {
    return myModule != null ? myModule.getModuleWithDependenciesAndLibrariesScope(false) : ProjectScope.getAllScope(project);
  }

  @Override
  public Object[] getCompletionVariants(String completionPrefix, PyExpression location, ProcessingContext context) {
    List<Object> variants = new ArrayList<Object>();
    final GlobalSearchScope scope = getScope(location.getProject());
    final PsiClass[] classes = myPackage.getClasses(scope);
    for (PsiClass psiClass : classes) {
      variants.add(LookupElementBuilder.create(psiClass).setIcon(psiClass.getIcon(0)));
    }
    final PsiPackage[] subPackages = myPackage.getSubPackages(scope);
    for (PsiPackage subPackage : subPackages) {
      variants.add(LookupElementBuilder.create(subPackage).setIcon(subPackage.getIcon(0)));
    }
    return ArrayUtil.toObjectArray(variants);
  }

  @Override
  public String getName() {
    return myPackage.getQualifiedName();
  }

  @Override
  public boolean isBuiltin(TypeEvalContext context) {
    return false;
  }
}

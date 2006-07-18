package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.psi.*;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 02.12.2003
 * Time: 16:49:25
 * To change this template use Options | File Templates.
 */
public class AllClassesGetter implements ContextGetter{
  @NonNls private static final String JAVA_PACKAGE_PREFIX = "java.";
  @NonNls private static final String JAVAX_PACKAGE_PREFIX = "javax.";

  public Object[] get(PsiElement context, CompletionContext completionContext) {
    if(context == null || !context.isValid()) return ArrayUtil.EMPTY_OBJECT_ARRAY;

    final List<PsiClass> classesList = new ArrayList<PsiClass>();
    final PsiManager manager = context.getManager();
    final PsiShortNamesCache cache = manager.getShortNamesCache();

    final GlobalSearchScope scope = context.getContainingFile().getResolveScope();
    final String[] names = cache.getAllClassNames(true);
    
    boolean lookingForAnnotations = false;
    final PsiElement prevSibling = context.getParent().getPrevSibling();
    if (prevSibling instanceof PsiJavaToken && 
        ((PsiJavaToken)prevSibling).getTokenType() == JavaTokenType.AT) {
      lookingForAnnotations = true;
    }

    for (final String name : names) {
      if (!completionContext.prefixMatches(name)) continue;
      final PsiClass[] classesByName = cache.getClassesByName(name, scope);
      
      for (PsiClass psiClass : classesByName) {
        if (lookingForAnnotations && !psiClass.isAnnotationType()) {
          continue;
        }
        if (CompletionUtil.isInExcludedPackage(psiClass)) {
          continue;
        }
        classesList.add(psiClass);
      }
    }

    Collections.sort(classesList, new Comparator<PsiClass>() {
      public int compare(PsiClass psiClass, PsiClass psiClass1) {
        if(manager.areElementsEquivalent(psiClass, psiClass1)) return 0;

        return getClassIndex(psiClass) - getClassIndex(psiClass1);
      }

      private int getClassIndex(PsiClass psiClass){
        if(psiClass.getManager().isInProject(psiClass)) return 2;
        final String qualifiedName = psiClass.getQualifiedName();
        if(qualifiedName.startsWith(JAVA_PACKAGE_PREFIX) ||
           qualifiedName.startsWith(JAVAX_PACKAGE_PREFIX)) return 1;
        return 0;
      }

      public boolean equals(Object o) {
        return o == this;
      }
    });

    return classesList.toArray(PsiClass.EMPTY_ARRAY);
  }
}

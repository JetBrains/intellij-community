package com.intellij.compiler.make;

import com.intellij.compiler.SymbolTable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.cls.ClsUtil;

public class ChangedRetentionPolicyDependencyProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.make.ChangedConstantsDependencyProcessor");
  private final Project myProject;
  private final CachingSearcher mySearcher;
  private DependencyCache myDependencyCache;

  public ChangedRetentionPolicyDependencyProcessor(Project project, CachingSearcher searcher, DependencyCache dependencyCache) {
    myProject = project;
    mySearcher = searcher;
    myDependencyCache = dependencyCache;
  }

  public void checkAnnotationRetentionPolicyChanges(final int annotationQName) throws CacheCorruptedException {
    final Cache oldCache = myDependencyCache.getCache();
    if (!ClsUtil.isAnnotation(oldCache.getFlags(oldCache.getClassId(annotationQName)))) {
      return;
    }
    if (!hasRetentionPolicyChanged(annotationQName, oldCache, myDependencyCache.getNewClassesCache(), myDependencyCache.getSymbolTable())) {
      return;
    }
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    final CacheCorruptedException[] _ex = new CacheCorruptedException[] {null};
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        try {
          final String qName = myDependencyCache.resolve(annotationQName);
          PsiClass[] classes = psiManager.findClasses(qName.replace('$', '.'), GlobalSearchScope.allScope(myProject));
          for (int i = 0; i < classes.length; i++) {
            final PsiClass aClass = classes[i];
            if (!aClass.isAnnotationType()) {
              continue;
            }
            final PsiReference[] references = mySearcher.findReferences(aClass, true);
            for (int j = 0; j < references.length; j++) {
              final PsiClass ownerClass = getOwnerClass(references[j].getElement());
              if (ownerClass != null && !ownerClass.equals(aClass)) {
                int qualifiedName = myDependencyCache.getSymbolTable().getId(ownerClass.getQualifiedName());
                if (myDependencyCache.markClass(qualifiedName, false)) {
                  if (LOG.isDebugEnabled()) {
                    LOG.debug("Marked dependent class "+myDependencyCache.resolve(qualifiedName) + "; reason: annotation's retention policy changed from SOURCE to CLASS or RUNTIME " + myDependencyCache.resolve(annotationQName));
                  }
                }
              }
            }
          }
        }
        catch (CacheCorruptedException e) {
         _ex[0] = e;
        }
      }
    });
    if (_ex[0] != null) {
      throw _ex[0];
    }
  }

  private boolean hasRetentionPolicyChanged(int annotationQName, final Cache oldCache, final Cache newCache, SymbolTable symbolTable) throws CacheCorruptedException {
    // if retention policy changed from SOURCE to CLASS or RUNTIME, all sources should be recompiled to propagate changes
    final int oldPolicy = MakeUtil.getAnnotationRetentionPolicy(annotationQName, oldCache, symbolTable);
    final int newPolicy = MakeUtil.getAnnotationRetentionPolicy(annotationQName, newCache, symbolTable);
    if ((oldPolicy == RetentionPolicies.SOURCE) && (newPolicy == RetentionPolicies.CLASS || newPolicy == RetentionPolicies.RUNTIME)) {
      return true;
    }
    return false;
  }

  private static PsiClass getOwnerClass(PsiElement element) {
    while (!(element instanceof PsiFile)) {
      if (element instanceof PsiClass && element.getParent() instanceof PsiJavaFile) { // top-level class
        return (PsiClass)element;
      }
      element = element.getParent();
    }
    return null;
  }
}

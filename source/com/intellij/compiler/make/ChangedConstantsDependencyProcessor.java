/**
 * created at Feb 24, 2002
 * @author Jeka
 */
package com.intellij.compiler.make;

import com.intellij.compiler.classParsing.FieldInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.IdentifierPosition;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtil;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class ChangedConstantsDependencyProcessor {
  public static boolean ENABLE_TRACING = false; // used by tests

  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.make.ChangedConstantsDependencyProcessor");
  private final Project myProject;
  private final CachingSearcher mySearcher;
  private DependencyCache myDependencyCache;
  private final int myQName;
  private FieldInfo[] myChangedFields;
  private FieldInfo[] myRemovedFields;


  public ChangedConstantsDependencyProcessor(Project project, CachingSearcher searcher, DependencyCache dependencyCache, int qName, FieldInfo[] changedFields, FieldInfo[] removedFields) {
    myProject = project;
    mySearcher = searcher;
    myDependencyCache = dependencyCache;
    myQName = qName;
    myChangedFields = changedFields;
    myRemovedFields = removedFields;
  }

  public void run() throws CacheCorruptedException {
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    final CacheCorruptedException[] _ex = new CacheCorruptedException[] {null};
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        try {
          final String qName = myDependencyCache.resolve(myQName);
          PsiClass[] classes = psiManager.findClasses(qName.replace('$', '.'), GlobalSearchScope.allScope(myProject));
          for (int i = 0; i < classes.length; i++) {
            PsiClass aClass = classes[i];
            if (ENABLE_TRACING) {
              System.out.println("Processing PsiClass " + aClass);
            }
            PsiField[] psiFields = aClass.getFields();
            for (int idx = 0; idx < psiFields.length; idx++) {
              PsiField psiField = psiFields[idx];
              if (isFieldChanged(psiField)) {
                processFieldChanged(psiField, aClass);
              }
            }
            for (int idx = 0; idx < myRemovedFields.length; idx++) {
              processFieldRemoved(myRemovedFields[idx], aClass);
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

  private void processFieldRemoved(FieldInfo info, PsiClass aClass) throws CacheCorruptedException {
    if (info.isPrivate()) {
      return; // optimization: don't need to search, cause may be used only in this class
    }
    SearchScope searchScope = GlobalSearchScope.projectScope(myProject);
    if (info.isPackageLocal()) {
      final PsiFile containingFile = aClass.getContainingFile();
      if (containingFile instanceof PsiJavaFile) {
        final String packageName = ((PsiJavaFile)containingFile).getPackageName();
        final PsiPackage aPackage = PsiManager.getInstance(myProject).findPackage(packageName);
        if (aPackage != null) {
          searchScope = GlobalSearchScope.packageScope(aPackage, false);
          searchScope = searchScope.intersectWith(aClass.getUseScope());
        }
      }
    }
    final PsiSearchHelper psiSearchHelper = PsiManager.getInstance(myProject).getSearchHelper();
    PsiIdentifier[] identifiers = psiSearchHelper.findIdentifiers(myDependencyCache.resolve(info.getName()), searchScope, IdentifierPosition.IN_CODE);
    for (int idx = 0; idx < identifiers.length; idx++) {
      PsiIdentifier identifier = identifiers[idx];
      PsiElement parent = identifier.getParent();
      if (parent instanceof PsiReferenceExpression) {
        PsiReferenceExpression refExpr = (PsiReferenceExpression)parent;
        PsiReference reference = refExpr.getReference();
        if (reference.resolve() == null) {
          PsiClass ownerClass = getOwnerClass(refExpr);
          if (ownerClass != null && !ownerClass.equals(aClass)) {
            int qualifiedName = myDependencyCache.getSymbolTable().getId(ownerClass.getQualifiedName());
            // should force marking of the class no matter was it compiled or not
            // This will ensure the class was recompiled _after_ all the constants get their new values
            if (myDependencyCache.markClass(qualifiedName, true)) {
              if (LOG.isDebugEnabled()) {
                LOG.debug("Mark dependent class "+ myDependencyCache.resolve(qualifiedName) + "; reason: some constants were removed from " + myDependencyCache.resolve(myQName));
              }
            }
          }
        }
      }
    }
  }

  private void processFieldChanged(PsiField field, PsiClass aClass) throws CacheCorruptedException {
    if (field.hasModifierProperty(PsiModifier.PRIVATE)) {
      return; // optimization: don't need to search, cause may be used only in this class
    }
    if (ENABLE_TRACING) {
      System.out.println("Processing changed field = " + field);
    }
    Set usages = new HashSet();
    addUsages(field, usages);
    if (LOG.isDebugEnabled()) {
      LOG.debug("++++++++++++++++++++++++++++++++++++++++++++++++");
      LOG.debug("Processing changed field: " + aClass.getQualifiedName() + "." + field.getName());
    }
    for (Iterator it = usages.iterator(); it.hasNext();) {
      PsiElement usage = (PsiElement)it.next();
      PsiClass ownerClass = getOwnerClass(usage);
      if (LOG.isDebugEnabled()) {
        if (ownerClass != null) {
          LOG.debug("Usage " + usage + " found in class: " + ownerClass.getQualifiedName());
        }
        else {
          LOG.debug("Usage " + usage + " found in class: null");
        }
      }
      if (ownerClass != null && !ownerClass.equals(aClass)) {
        int qualifiedName = myDependencyCache.getSymbolTable().getId(ownerClass.getQualifiedName());
        // should force marking of the class no matter was it compiled or not
        // This will ensure the class was recompiled _after_ all the constants get their new values
        if (LOG.isDebugEnabled()) {
          LOG.debug("Marking class id = [" + qualifiedName + "], name=[" + myDependencyCache.resolve(qualifiedName) + "]");
        }
        if (myDependencyCache.markClass(qualifiedName, true)) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Marked dependent class "+myDependencyCache.resolve(qualifiedName) + "; reason: constants changed in " + myDependencyCache.resolve(myQName));
          }
        }
      }
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("+++++++++++++++++++++++++++++++++++++++++++++++");
    }
  }

  private void addUsages(PsiField psiField, Collection usages) {
    PsiReference[] references = mySearcher.findReferences(psiField)/*doFindReferences(searchHelper, psiField)*/;
    if (ENABLE_TRACING) {
      System.out.println("Found " + references.length + " references to field " + psiField);
    }
    for (int idx = 0; idx < references.length; idx++) {
      final PsiReference ref = references[idx];
      if (!(ref instanceof PsiReferenceExpression)) {
        continue;
      }
      if (ENABLE_TRACING) {
        System.out.println("Checking reference " + ref);
      }
      PsiElement e = ref.getElement();
      usages.add(e);
      PsiField ownerField = getOwnerField(e);
      if (ownerField != null) {
        if (ownerField.hasModifierProperty(PsiModifier.FINAL)) {
          PsiExpression initializer = ownerField.getInitializer();
          if (initializer != null && PsiUtil.isConstantExpression(initializer)) {
            // if the field depends on the compile-time-constant expression and is itself final
            addUsages(ownerField, usages);
          }
        }
      }
    }
  }

  /*
  private PsiReference[] doFindReferences(final PsiSearchHelper searchHelper, final PsiField psiField) {
    final ProgressManager progressManager = ProgressManager.getInstance();
    final ProgressIndicator currentProgress = progressManager.getProgressIndicator();
    final PsiReference[][] references = new PsiReference[][] {null};
    progressManager.runProcess(new Runnable() {
      public void run() {
        references[0] = searchHelper.findReferences(psiField, GlobalSearchScope.projectScope(myProject), false);
        if (ENABLE_TRACING) {
          System.out.println("Finding referencers for " + psiField);
        }
      }
    }, new NonCancellableProgressAdapter(currentProgress));
    return references[0];
  }
  */

  private PsiField getOwnerField(PsiElement element) {
    while (!(element instanceof PsiFile)) {
      if (element instanceof PsiClass) {
        break;
      }
      if (element instanceof PsiField) { // top-level class
        return (PsiField)element;
      }
      element = element.getParent();
    }
    return null;
  }

  private boolean isFieldChanged(PsiField field) throws CacheCorruptedException {
    String name = field.getName();
    for (int idx = 0; idx < myChangedFields.length; idx++) {
      if (name.equals(myDependencyCache.resolve(myChangedFields[idx].getName()))) {
        return true;
      }
    }
    return false;
  }

  private PsiClass getOwnerClass(PsiElement element) {
    while (!(element instanceof PsiFile)) {
      if (element instanceof PsiClass && element.getParent() instanceof PsiJavaFile) { // top-level class
        return (PsiClass)element;
      }
      element = element.getParent();
    }
    return null;
  }
}

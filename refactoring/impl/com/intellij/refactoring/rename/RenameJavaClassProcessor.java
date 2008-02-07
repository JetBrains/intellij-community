package com.intellij.refactoring.rename;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * @author yole
 */
public class RenameJavaClassProcessor extends RenamePsiElementProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.RenameJavaClassProcessor");

  public boolean canProcessElement(final PsiElement element) {
    return element instanceof PsiClass;
  }

  public void renameElement(final PsiElement element,
                            final String newName,
                            final UsageInfo[] usages, final RefactoringElementListener listener) throws IncorrectOperationException {
    PsiClass aClass = (PsiClass) element;
    ArrayList<UsageInfo> postponedCollisions = new ArrayList<UsageInfo>();
    // rename all references
    for (final UsageInfo usage : usages) {
      if (usage instanceof ResolvableCollisionUsageInfo) {
        if (usage instanceof CollidingClassImportUsageInfo) {
          ((CollidingClassImportUsageInfo)usage).getImportStatement().delete();
        }
        else {
          postponedCollisions.add(usage);
        }
      }
    }

    // do actual rename
    ChangeContextUtil.encodeContextInfo(aClass, true);
    PsiFile psiFile = aClass.getContainingFile();
    Document document = psiFile == null ? null : PsiDocumentManager.getInstance(aClass.getProject()).getDocument(psiFile);
    aClass.setName(newName);

    for (UsageInfo usage : usages) {
      if (!(usage instanceof ResolvableCollisionUsageInfo)) {
        final PsiReference ref = usage.getReference();
        if (ref == null) continue;
        try {
          ref.bindToElement(aClass);
        }
        catch (IncorrectOperationException e) {//fall back to old scheme
          ref.handleElementRename(newName);
        }
      }
    }

    ChangeContextUtil.decodeContextInfo(aClass, null, null); //to make refs to other classes from this one resolve to their old referent

    // resolve collisions
    for (UsageInfo postponedCollision : postponedCollisions) {
      ClassHidesImportedClassUsageInfo collision = (ClassHidesImportedClassUsageInfo) postponedCollision;
      collision.resolveCollision();
    }
    listener.elementRenamed(aClass);
    if (document != null) {
      // make highlighting consistent
      ((DocumentEx)document).setModificationStamp(psiFile.getModificationStamp());
    }
  }

  @Nullable
  public Pair<String, String> getTextOccurrenceSearchStrings(final PsiElement element, final String newName) {
    if (element instanceof PsiClass) {
      final PsiClass aClass = (PsiClass)element;
      if (aClass.getParent() instanceof PsiClass) {
        final String dollaredStringToSearch = ClassUtil.getJVMClassName(aClass);
        final String dollaredStringToReplace = dollaredStringToSearch == null ? null : RefactoringUtil.getNewInnerClassName(aClass, dollaredStringToSearch, newName);
        if (dollaredStringToReplace != null) {
          return new Pair<String, String>(dollaredStringToSearch, dollaredStringToReplace);
        }
      }
    }
    return null;
  }

  public String getQualifiedNameAfterRename(final PsiElement element, final String newName, final boolean nonJava) {
    if (nonJava) {
      final PsiClass aClass = (PsiClass)element;
      return RenameUtil.getQualifiedNameAfterRename(aClass.getQualifiedName(), newName);
    }
    else {
      return newName;
    }
  }

  public void prepareRenaming(final PsiElement element, final String newName, final Map<PsiElement, String> allRenames) {
    final PsiMethod[] constructors = ((PsiClass) element).getConstructors();
    for (PsiMethod constructor : constructors) {
      allRenames.put(constructor, newName);
    }
  }


  public void findExistingNameConflicts(final PsiElement element, final String newName, final Collection<String> conflicts) {
    if (element instanceof PsiCompiledElement) return;
    final PsiClass aClass = (PsiClass)element;
    if (newName.equals(aClass.getName())) return;
    final PsiClass containingClass = aClass.getContainingClass();
    if (containingClass != null) { // innerClass
      PsiClass[] innerClasses = containingClass.getInnerClasses();
      for (PsiClass innerClass : innerClasses) {
        if (newName.equals(innerClass.getName())) {
          conflicts.add(RefactoringBundle.message("inner.class.0.is.already.defined.in.class.1", newName, containingClass.getQualifiedName()));
          break;
        }
      }
    }
    else {
      final String qualifiedNameAfterRename = RenameUtil.getQualifiedNameAfterRename(aClass.getQualifiedName(), newName);
      Project project = element.getProject();
      final PsiClass conflictingClass =
        JavaPsiFacade.getInstance(project).findClass(qualifiedNameAfterRename, GlobalSearchScope.allScope(project));
      if (conflictingClass != null) {
        conflicts.add(RefactoringBundle.message("class.0.already.exists", qualifiedNameAfterRename));
      }
    }
  }
}

package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.NonCodeUsageInfo;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author yole
 */
public class MoveClassToInnerProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.moveClassesOrPackages.MoveClassToInnerProcessor");

  private PsiClass myClassToMove;
  private PsiClass myTargetClass;
  private boolean mySearchInComments;
  private boolean mySearchInNonJavaFiles;
  private NonCodeUsageInfo[] myNonCodeUsages;
  private static final Key<List<NonCodeUsageInfo>> ourNonCodeUsageKey = Key.create("MoveClassToInner.NonCodeUsage");

  public MoveClassToInnerProcessor(Project project,
                                   final PsiClass classToMove,
                                   @NotNull final PsiClass targetClass,
                                   boolean searchInComments,
                                   boolean searchInNonJavaFiles) {
    super(project);
    myClassToMove = classToMove;
    myTargetClass = targetClass;
    mySearchInComments = searchInComments;
    mySearchInNonJavaFiles = searchInNonJavaFiles;
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new MoveClassesOrPackagesViewDescriptor(new PsiElement[] { myClassToMove },
                                                   mySearchInComments, mySearchInNonJavaFiles,
                                                   myTargetClass.getQualifiedName());
  }

  @NotNull
  public UsageInfo[] findUsages() {
    List<UsageInfo> usages = new ArrayList<UsageInfo>();
    String newName = myTargetClass.getQualifiedName() + "." + myClassToMove.getName();
    Collections.addAll(usages, MoveClassesOrPackagesUtil.findUsages(myClassToMove, mySearchInComments,
                                                                    mySearchInNonJavaFiles, newName));
    for (Iterator<UsageInfo> iterator = usages.iterator(); iterator.hasNext();) {
      UsageInfo usageInfo = iterator.next();
      if (!(usageInfo instanceof NonCodeUsageInfo) && PsiTreeUtil.isAncestor(myClassToMove, usageInfo.getElement(), false)) {
        iterator.remove();
      }
    }
    return usages.toArray(new UsageInfo[usages.size()]);
  }

  protected void refreshElements(PsiElement[] elements) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  protected void performRefactoring(UsageInfo[] usages) {
    try {
      saveNonCodeUsages(usages);
      ChangeContextUtil.encodeContextInfo(myClassToMove, true);
      PsiClass newClass = (PsiClass)myTargetClass.addBefore(myClassToMove, myTargetClass.getRBrace());
      newClass.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
      newClass = (PsiClass)ChangeContextUtil.decodeContextInfo(newClass, null, null);

      retargetClassRefs(myClassToMove, newClass);

      Map<PsiElement, PsiElement> oldToNewElementsMapping = new HashMap<PsiElement, PsiElement>();
      oldToNewElementsMapping.put(myClassToMove, newClass);
      myNonCodeUsages = MoveClassesOrPackagesProcessor.retargetUsages(usages, oldToNewElementsMapping);
      retargetNonCodeUsages(newClass);

      PsiManager.getInstance(myProject).getCodeStyleManager().removeRedundantImports((PsiJavaFile)newClass.getContainingFile());

      myClassToMove.delete();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private void saveNonCodeUsages(final UsageInfo[] usages) {
    for(UsageInfo usageInfo: usages) {
      if (usageInfo instanceof NonCodeUsageInfo) {
        final NonCodeUsageInfo nonCodeUsage = (NonCodeUsageInfo)usageInfo;
        PsiElement element = nonCodeUsage.getElement();
        if (PsiTreeUtil.isAncestor(myClassToMove, element, false)) {
          List<NonCodeUsageInfo> list = element.getCopyableUserData(ourNonCodeUsageKey);
          if (list == null) {
            list = new ArrayList<NonCodeUsageInfo>();
            element.putCopyableUserData(ourNonCodeUsageKey, list);
          }
          list.add(nonCodeUsage);
        }
      }
    }
  }

  private void retargetNonCodeUsages(final PsiClass newClass) {
    newClass.accept(new PsiRecursiveElementVisitor() {
      public void visitElement(final PsiElement element) {
        super.visitElement(element);
        List<NonCodeUsageInfo> list = element.getCopyableUserData(ourNonCodeUsageKey);
        if (list != null) {
          for(NonCodeUsageInfo info: list) {
            for(int i=0; i<myNonCodeUsages.length; i++) {
              if (myNonCodeUsages [i] == info) {
                myNonCodeUsages [i] = info.replaceElement(element);
                break;
              }
            }
          }
          element.putCopyableUserData(ourNonCodeUsageKey, null);
        }
      }
    });
  }

  protected void performPsiSpoilingRefactoring() {
    RefactoringUtil.renameNonCodeUsages(myProject, myNonCodeUsages);
  }

  private static void retargetClassRefs(final PsiClass classToMove, final PsiClass newClass) {
    newClass.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceElement(final PsiJavaCodeReferenceElement reference) {
        PsiElement element = reference.resolve();
        if (element instanceof PsiClass && PsiTreeUtil.isAncestor(classToMove, element, false)) {
          PsiClass newInnerClass = findMatchingClass(classToMove, newClass, (PsiClass) element);
          try {
            reference.bindToElement(newInnerClass);
          }
          catch(IncorrectOperationException ex) {
            LOG.error(ex);
          }
        }
        else {
          super.visitReferenceElement(reference);
        }
      }
    });
  }

  private static PsiClass findMatchingClass(final PsiClass classToMove, final PsiClass newClass, final PsiClass innerClass) {
    if (classToMove == innerClass) {
      return newClass;
    }
    PsiClass parentClass = findMatchingClass(classToMove, newClass, innerClass.getContainingClass());
    PsiClass newInnerClass = parentClass.findInnerClassByName(innerClass.getName(), false);
    assert newInnerClass != null;
    return newInnerClass;
  }

  protected String getCommandName() {
    return RefactoringBundle.message("move.class.to.inner.command.name",
                                     myClassToMove.getQualifiedName(),
                                     myTargetClass.getQualifiedName());
  }
}

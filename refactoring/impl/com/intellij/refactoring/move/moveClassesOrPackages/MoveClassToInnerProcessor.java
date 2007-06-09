package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;

/**
 * @author yole
 */
public class MoveClassToInnerProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.moveClassesOrPackages.MoveClassToInnerProcessor");

  private PsiClass myClassToMove;
  private PsiClass myTargetClass;
  private boolean mySearchInComments;
  private boolean mySearchInNonJavaFiles;

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
  protected UsageInfo[] findUsages() {
    MoveClassUsageCollector collector = new MoveClassUsageCollector(new PsiElement[] { myClassToMove }, mySearchInComments, mySearchInNonJavaFiles) {
      protected String getNewName(final PsiElement element) {
        return myTargetClass.getQualifiedName() + "." + ((PsiClass) element).getName();
      }
    };
    List<UsageInfo> usages = collector.collectUsages();
    for (Iterator<UsageInfo> iterator = usages.iterator(); iterator.hasNext();) {
      UsageInfo usageInfo = iterator.next();
      if (PsiTreeUtil.isAncestor(myClassToMove, usageInfo.getElement(), false)) {
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
      ChangeContextUtil.encodeContextInfo(myClassToMove, true);
      PsiClass newClass = (PsiClass)myTargetClass.addBefore(myClassToMove, myTargetClass.getRBrace());
      newClass.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
      newClass = (PsiClass)ChangeContextUtil.decodeContextInfo(newClass, null, null);

      retargetClassRefs(myClassToMove, newClass);

      Map<PsiElement, PsiElement> oldToNewElementsMapping = new HashMap<PsiElement, PsiElement>();
      oldToNewElementsMapping.put(myClassToMove, newClass);
      MoveClassesOrPackagesProcessor.retargetUsages(usages, oldToNewElementsMapping);

      PsiManager.getInstance(myProject).getCodeStyleManager().removeRedundantImports((PsiJavaFile)newClass.getContainingFile());

      myClassToMove.delete();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
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

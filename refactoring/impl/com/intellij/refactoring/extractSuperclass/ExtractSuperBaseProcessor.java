package com.intellij.refactoring.extractSuperclass;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.turnRefsToSuper.TurnRefsToSuperProcessorBase;
import com.intellij.refactoring.util.JavaDocPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * @author dsl
 */
public abstract class ExtractSuperBaseProcessor extends TurnRefsToSuperProcessorBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.extractSuperclass.ExtractSuperClassProcessor");
  protected PsiDirectory myTargetDirectory;
  protected final String myNewClassName;
  protected MemberInfo[] myMemberInfos;
  protected final JavaDocPolicy myJavaDocPolicy;


  public ExtractSuperBaseProcessor(Project project,
                                   boolean replaceInstanceOf,
                                   PsiDirectory targetDirectory,
                                   String newClassName,
                                   PsiClass aClass, MemberInfo[] memberInfos, JavaDocPolicy javaDocPolicy) {
    super(project, replaceInstanceOf, newClassName);
    myTargetDirectory = targetDirectory;
    myNewClassName = newClassName;
    myClass = aClass;
    myMemberInfos = memberInfos;
    myJavaDocPolicy = javaDocPolicy;
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new ExtractSuperClassViewDescriptor(myTargetDirectory, myClass, myMemberInfos);
  }

  protected boolean doesAnyExtractedInterfaceExtends(PsiClass aClass) {
    for (final MemberInfo memberInfo : myMemberInfos) {
      final PsiElement member = memberInfo.getMember();
      if (member instanceof PsiClass && memberInfo.getOverrides() != null) {
        if (InheritanceUtil.isInheritorOrSelf((PsiClass)member, aClass, true)) {
          return true;
        }
      }
    }
    return false;
  }

  protected boolean doMemberInfosContain(PsiMethod method) {
    for (final MemberInfo info : myMemberInfos) {
      if (info.getMember() instanceof PsiMethod) {
        if (MethodSignatureUtil.areSignaturesEqual(method, (PsiMethod)info.getMember())) return true;
      }
      else if (info.getMember() instanceof PsiClass && info.getOverrides() != null) {
        final PsiMethod methodBySignature = ((PsiClass)info.getMember()).findMethodBySignature(method, true);
        if (methodBySignature != null) {
          return true;
        }
      }
    }
    return false;
  }

  protected boolean doMemberInfosContain(final PsiField field) {
    for (final MemberInfo info : myMemberInfos) {
      if (myManager.areElementsEquivalent(field, info.getMember())) return true;
    }
    return false;
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    PsiReference[] refs = mySearchHelper.findReferences(myClass, GlobalSearchScope.projectScope(myProject), false);
    final ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();
    detectTurnToSuperRefs(refs, result);
    final PsiPackage originalPackage = myClass.getContainingFile().getContainingDirectory().getPackage();
    if (Comparing.equal(myTargetDirectory.getPackage(), originalPackage)) {
      result.clear();
    }
    for (final PsiReference ref : refs) {
      final PsiElement element = ref.getElement();
      if (!canTurnToSuper(element)) {
        result.add(new BindToOldUsageInfo(element, ref, myClass));
      }
    }
    UsageInfo[] usageInfos = result.toArray(new UsageInfo[result.size()]);
    return UsageViewUtil.removeDuplicatedUsages(usageInfos);
  }

  protected void performRefactoring(UsageInfo[] usages) {
    try {
      final String superClassName = myClass.getName();
      final String oldQualifiedName = myClass.getQualifiedName();
      myClass.setName(myNewClassName);
      PsiClass superClass = extractSuper(superClassName);
      for (final UsageInfo usage : usages) {
        if (usage instanceof BindToOldUsageInfo) {
          final PsiReference reference = usage.getReference();
          if (reference != null && reference.getElement().isValid()) {
            reference.bindToElement(myClass);
          }
        }
      }
      if (!Comparing.equal(oldQualifiedName, superClass.getQualifiedName())) {
        processTurnToSuperRefs(usages, superClass);
      }
      final PsiFile containingFile = myClass.getContainingFile();
      if (containingFile instanceof PsiJavaFile) {
        CodeStyleManager.getInstance(myManager).removeRedundantImports((PsiJavaFile) containingFile);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    performVariablesRenaming();
  }

  protected abstract PsiClass extractSuper(String superClassName) throws IncorrectOperationException;

  protected void refreshElements(PsiElement[] elements) {
    myClass = (PsiClass)elements[0];
    myTargetDirectory = (PsiDirectory)elements[1];
    for (int i = 0; i < myMemberInfos.length; i++) {
      final MemberInfo info = myMemberInfos[i];
      info.updateMember((PsiMember)elements[i + 2]);
    }
  }

  protected String getCommandName() {
    return RefactoringBundle.message("extract.subclass.command");
  }
}

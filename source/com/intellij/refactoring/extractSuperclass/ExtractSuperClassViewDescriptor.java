package com.intellij.refactoring.extractSuperclass;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;

/**
 * @author dsl
 */
public class ExtractSuperClassViewDescriptor extends UsageViewDescriptorAdapter {
  final PsiElement[] myElements;

  public ExtractSuperClassViewDescriptor(UsageInfo[] usages,
                                         PsiDirectory targetDirectory,
                                         PsiClass subclass,
                                         MemberInfo[] infos,
                                         FindUsagesCommand refreshCommand) {
    super(usages, refreshCommand);
    myElements = new PsiElement[infos.length + 2];
    myElements[0] = subclass;
    myElements[1] = targetDirectory;
    for (int i = 0; i < infos.length; i++) {
      final MemberInfo info = infos[i];
      myElements[i + 2] = info.getMember();
    }
  }

  public PsiElement[] getElements() {
    return myElements;
  }

  public void refresh(PsiElement[] elements) {
    System.arraycopy(elements, 0, myElements, 0, elements.length);
    if (myRefreshCommand != null) {
      myUsages = myRefreshCommand.execute(elements);
    }
  }

  public String getProcessedElementsHeader() {
    return RefactoringBundle.message("extract.superclass.elements.header");
  }
}

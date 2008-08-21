package com.intellij.refactoring.introduceparameterobject;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.psi.MyUsageViewUtil;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;

class IntroduceParameterObjectUsageViewDescriptor extends UsageViewDescriptorAdapter {

   private PsiMethod method;

    IntroduceParameterObjectUsageViewDescriptor(PsiMethod method) {

       this.method = method;
   }

   public PsiElement[] getElements() {
       return new PsiElement[]{method};
   }
   public String getProcessedElementsHeader() {
       return RefactorJBundle.message("method.whose.parameters.are.to.wrapped");
   }

   public String getCodeReferencesText(int usagesCount, int filesCount) {
       return RefactorJBundle.message("references.to.be.modified") + MyUsageViewUtil.getUsageCountInfo(usagesCount, filesCount, "reference");
   }
}

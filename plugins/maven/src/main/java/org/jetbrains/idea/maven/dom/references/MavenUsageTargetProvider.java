package org.jetbrains.idea.maven.dom.references;

import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetProvider;

public class MavenUsageTargetProvider implements UsageTargetProvider {
  public UsageTarget[] getTargets(Editor editor, PsiFile file) {
    PsiElement target = MavenTargetUtil.getFindTarget(editor, file);
    if (target == null) return UsageTarget.EMPTY_ARRAY;
    return new UsageTarget[]{new PsiElement2UsageTargetAdapter(target)};
  }

  public UsageTarget[] getTargets(PsiElement psiElement) {
    return UsageTarget.EMPTY_ARRAY;
  }
}

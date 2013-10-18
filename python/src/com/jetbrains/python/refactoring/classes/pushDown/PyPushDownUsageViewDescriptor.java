package com.jetbrains.python.refactoring.classes.pushDown;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dennis.Ushakov
 */
public class PyPushDownUsageViewDescriptor implements UsageViewDescriptor {
  private final PyClass myClass;
  private static final String HEADER = RefactoringBundle.message("push.down.members.elements.header");

  public PyPushDownUsageViewDescriptor(final PyClass clazz) {
    myClass = clazz;
  }

  @NotNull
  public PsiElement[] getElements() {
    return new PsiElement[] {myClass};
  }

  public String getProcessedElementsHeader() {
    return HEADER;
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("classes.to.push.down.members.to", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  @Nullable
  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }
}

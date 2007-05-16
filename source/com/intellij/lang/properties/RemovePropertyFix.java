package com.intellij.lang.properties;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
class RemovePropertyFix implements IntentionAction {
  private final Property myProperty;

  public RemovePropertyFix(@NotNull final Property origProperty) {
    myProperty = origProperty;
  }

  @NotNull
  public String getText() {
    return PropertiesBundle.message("remove.property.intention.text");
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return file.isValid()
           && myProperty != null
           && myProperty.isValid()
           && myProperty.getManager().isInProject(myProperty)
      ;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    myProperty.delete();
  }

  public boolean startInWriteAction() {
    return true;
  }
}

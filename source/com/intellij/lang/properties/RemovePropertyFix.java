package com.intellij.lang.properties;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;

/**
 * @author cdr
 */
class RemovePropertyFix implements IntentionAction {
  private final Property myOrigProperty;

  public RemovePropertyFix(final Property origProperty) {
    myOrigProperty = origProperty;
  }

  public String getText() {
    return PropertiesBundle.message("remove.property.intention.text");
  }

  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return file.isValid()
           && myOrigProperty != null
           && myOrigProperty.isValid()
      ;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    myOrigProperty.delete();
  }

  public boolean startInWriteAction() {
    return true;
  }
}

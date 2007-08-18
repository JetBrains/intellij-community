package com.intellij.refactoring.actions;

import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.lang.html.ExtractIncludeFromHTMLHandler;
import com.intellij.refactoring.lang.jsp.extractInclude.ExtractJspIncludeFileHandler;

/**
 * @author ven
 */
public class ExtractIncludeAction extends BaseRefactoringAction {
  public boolean isAvailableInEditorOnly() {
    return true;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    return false;
  }

  protected boolean isAvailableForLanguage(Language language) {
    return true;
  }

  protected boolean isAvailableForFile(PsiFile file) {
    return PsiUtil.isInJspFile(file) || Language.findInstance(HTMLLanguage.class).equals(file.getLanguage()) ||
      Language.findInstance(XHTMLLanguage.class).equals(file.getLanguage());
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    PsiFile file = DataKeys.PSI_FILE.getData(dataContext);
    if (PsiUtil.isInJspFile(file)) {
      return new ExtractJspIncludeFileHandler(file);
    }
    else if (Language.findInstance(HTMLLanguage.class).equals(file.getLanguage()) ||
             Language.findInstance(XHTMLLanguage.class).equals(file.getLanguage())) {
      return new ExtractIncludeFromHTMLHandler(file);
    }
    return null;
  }
}

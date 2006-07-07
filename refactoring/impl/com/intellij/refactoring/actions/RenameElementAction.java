package com.intellij.refactoring.actions;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.psi.xml.XmlFile;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import com.intellij.xml.util.XmlUtil;

public class RenameElementAction extends BaseRefactoringAction {

  public RenameElementAction() {
    setInjectedContext(true);
  }

  public boolean isAvailableInEditorOnly() {
    return false;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    if (elements.length != 1) return false;

    PsiElement element = elements[0];
    if (element instanceof JspClass || element instanceof JspHolderMethod) return false;
    if (element instanceof PsiNamedElement) return true;

    if (element.getContainingFile() instanceof XmlFile){
      XmlFile xmlFile = (XmlFile)element.getContainingFile();
      return XmlUtil.isInAntBuildFile(xmlFile);
    }

    return false;
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    return RenameHandlerRegistry.getInstance().getRenameHandler(dataContext);
  }

  protected boolean isEnabledOnDataContext(DataContext dataContext) {
    return RenameHandlerRegistry.getInstance().hasAvailableHandler(dataContext);
  }

  protected boolean isAvailableForLanguage(Language language) {
    return true;
  }
}

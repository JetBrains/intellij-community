package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileTypes.FileTypeSupportCapabilities;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.jsp.JspxFileImpl;
import com.intellij.psi.xml.XmlFile;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import com.intellij.xml.util.XmlUtil;

public class RenameElementAction extends BaseRefactoringAction {

  public boolean isAvailableInEditorOnly() {
    return false;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    if (elements.length != 1) return false;

    PsiElement element = elements[0];

    if (element instanceof PsiNamedElement)
      return true;

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
    return RenameHandlerRegistry.getInstance().getRenameHandler(dataContext) != null;
  }

  protected boolean isAvaiableForFile(PsiFile file) {
    final FileTypeSupportCapabilities supportCapabilities = file.getFileType().getSupportCapabilities();

    if (supportCapabilities != null && supportCapabilities.hasRename()) {
      return true;
    }

    if (file instanceof XmlFile) return true;
    return super.isAvaiableForFile(file);
  }
}

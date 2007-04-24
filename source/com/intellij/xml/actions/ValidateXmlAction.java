package com.intellij.xml.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author mike
 */
public class ValidateXmlAction extends AnAction /*extends BaseCodeInsightAction*/ {
  public ValidateXmlAction() {
  }

  private CodeInsightActionHandler getHandler(final @NotNull PsiFile file) {
    ValidateXmlActionHandler handler = new ValidateXmlActionHandler(true);
    handler.setErrorReporter(
      handler.new StdErrorReporter(
        file.getProject(),
        new Runnable() {
          public void run() {
            doRunAction(file);
          }
        }
      )
    );
    return handler;
  }

  public void actionPerformed(AnActionEvent e) {
    final PsiFile psiFile = (PsiFile) e.getDataContext().getData(DataConstants.PSI_FILE);
    if (psiFile == null) return;
    doRunAction(psiFile);
  }

  private void doRunAction(final @NotNull PsiFile psiFile) {
    final Project project = psiFile.getProject();

    CommandProcessor.getInstance().executeCommand(
        project, new Runnable(){
        public void run(){
          final Runnable action = new Runnable() {
            public void run() {
              getHandler(psiFile).invoke(project, null, psiFile);
            }
          };
          ApplicationManager.getApplication().runWriteAction(action);
        }
      },
      getCommandName(),
      null
    );
  }

  private String getCommandName(){
    String text = getTemplatePresentation().getText();
    return text != null ? text : "";
  }

  public void update(AnActionEvent event) {
    super.update(event);

    Presentation presentation = event.getPresentation();
    PsiElement psiElement = (PsiElement)event.getDataContext().getData(DataConstants.PSI_FILE);

    boolean flag = psiElement instanceof XmlFile;
    presentation.setVisible(flag);
    boolean value = psiElement instanceof XmlFile;

    if (value) {
      final PsiFile containingFile = psiElement.getContainingFile();

      if (containingFile!=null &&
          (containingFile.getFileType() == StdFileTypes.XML ||
           containingFile.getFileType() == StdFileTypes.XHTML
          )) {
        value = true;
      } else {
        value = false;
      }
    }

    presentation.setEnabled(value);
  }
}

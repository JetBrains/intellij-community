package com.intellij.ide.fileTemplates.ui;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import org.apache.velocity.runtime.parser.ParseException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Properties;

public class CreateFromTemplateDialog extends DialogWrapper {
  @NotNull private final PsiDirectory myDirectory;
  @NotNull private final Project myProject;
  private PsiElement myCreatedElement;
  private final CreateFromTemplatePanel myAttrPanel;
  private final JComponent myAttrComponent;
  @NotNull private final FileTemplate myTemplate;
  private Properties myDefaultProperties;

  public CreateFromTemplateDialog(@NotNull Project project, @NotNull PsiDirectory directory, @NotNull FileTemplate template) {
    super(project, true);
    myDirectory = directory;
    myProject = project;
    myTemplate = template;
    setTitle(IdeBundle.message("title.new.from.template", template.getName()));

    PsiPackage aPackage = myDirectory.getPackage();
    String packageName = aPackage == null ? "" : aPackage.getQualifiedName();
    myDefaultProperties = FileTemplateManager.getInstance().getDefaultProperties();
    myDefaultProperties.setProperty(FileTemplate.ATTRIBUTE_PACKAGE_NAME, packageName);

    String[] unsetAttributes = null;
    try {
      unsetAttributes = myTemplate.getUnsetAttributes(myDefaultProperties);
    }
    catch (ParseException e) {
      showErrorDialog(e);
    }

    if (unsetAttributes != null) {
      myAttrPanel = new CreateFromTemplatePanel(unsetAttributes, !myTemplate.isJavaClassTemplate());
      myAttrComponent = myAttrPanel.getComponent();
      init();
    }
    else {
      myAttrPanel = null;
      myAttrComponent = null;
    }
  }

  public PsiElement create(){
    if (myAttrPanel != null) {
      if (myAttrPanel.hasSomethingToAsk()) {
        show();
      }
      else {
        doCreate(null);
      }
    }
    return myCreatedElement;
  }

  protected void doOKAction(){
    String fileName = myAttrPanel.getFileName();
    if (fileName != null && fileName.length() == 0) {
      Messages.showMessageDialog(myAttrComponent, IdeBundle.message("error.please.enter.a.file.name"), CommonBundle.getErrorTitle(),
                                 Messages.getErrorIcon());
      return;
    }
    doCreate(fileName);
    if ( myCreatedElement != null ) {
      super.doOKAction();
    }
  }

  private void doCreate(final String fileName)  {
    try {
      myCreatedElement = FileTemplateUtil.createFromTemplate(myTemplate, fileName, myAttrPanel.getProperties(myDefaultProperties),
                                                             myDirectory);
    }
    catch (Exception e) {
      showErrorDialog(e);
    }
  }

  private void showErrorDialog(final Exception e) {
    Messages.showMessageDialog(myProject, filterMessage(e.getMessage()), getErrorMessage(), Messages.getErrorIcon());
  }

  private String getErrorMessage() {
    return myTemplate.isJavaClassTemplate() ? IdeBundle.message("title.cannot.create.class") : IdeBundle.message("title.cannot.create.file");
  }

  @Nullable
  private String filterMessage(String message){
    if (message == null) return null;
    @NonNls String ioExceptionPrefix = "java.io.IOException:";
    if (message.startsWith(ioExceptionPrefix)){
      message = message.substring(ioExceptionPrefix.length());
    }
    return IdeBundle.message("error.unable.to.parse.template.message", myTemplate.getName(), message);
  }

  protected JComponent createCenterPanel(){
    myAttrPanel.ensureFitToScreen(200, 200);
    JPanel centerPanel = new JPanel(new GridBagLayout());
    centerPanel.add(myAttrComponent, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(2, 2, 2, 2), 0, 0));
    return centerPanel;
  }

  public JComponent getPreferredFocusedComponent(){
    return IdeFocusTraversalPolicy.getPreferredFocusedComponent(myAttrComponent);
  }
}

package com.intellij.ide.fileTemplates.ui;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.CommonBundle;
import org.apache.velocity.runtime.parser.ParseException;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.Properties;

public class CreateFromTemplateDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.fileTemplates.ui.CreateFromTemplateDialog");
  private final PsiDirectory myDirectory;
  private final Project myProject;
  private PsiElement myCreatedElement;
  private final CreateFromTemplatePanel myAttrPanel;
  private final JComponent myAttrComponent;
  private final FileTemplate myTemplate;

  public CreateFromTemplateDialog(Project project, PsiDirectory directory, FileTemplate template) throws ParseException {
    super(project, true);
    LOG.assertTrue(directory != null, "Directory cannot be null");
    LOG.assertTrue(template != null, "Template cannot be null");
    myDirectory = directory;
    myProject = project;
    myTemplate = template;
    setTitle(IdeBundle.message("title.new.from.template", template.getName()));

    PsiPackage aPackage = myDirectory.getPackage();
    String packageName = aPackage == null ? "" : aPackage.getQualifiedName();
    Properties defaultProperties = FileTemplateManager.getInstance().getDefaultProperties();
    defaultProperties.setProperty(FileTemplateUtil.PACKAGE_ATTR, packageName);
    myAttrPanel = new CreateFromTemplatePanel(myTemplate, defaultProperties);
    myAttrComponent = myAttrPanel.getComponent();
    init();
  }

  public PsiElement getCreatedElement(){
    return myCreatedElement;
  }

  protected void doOKAction(){
    if(myTemplate == null){
      return;
    }
    String fileName = null;
    Properties props = myAttrPanel.getProperties();
    if (!myTemplate.isJavaClassTemplate()){
      fileName = myAttrPanel.getFileName();
      if (fileName.length() == 0){
        Messages.showMessageDialog(myAttrComponent, IdeBundle.message("error.please.enter.a.file.name"),
                                   CommonBundle.getErrorTitle(), Messages.getErrorIcon());
        return;
      }
    }
    try{
      PsiElement[] element = new PsiElement[1];
      FileTemplateUtil.createFromTemplate(element, myTemplate, fileName, props, myProject, myDirectory);
      myCreatedElement = element[0];
      super.doOKAction();
    }
    catch (Exception e){
      showErrorMessage(myAttrComponent, myTemplate, e);
    }
  }

  private static void showErrorMessage(Component parentComponent, FileTemplate template, Exception e){
    Messages.showMessageDialog(parentComponent, filterMessage(e.getMessage()),
                               template.isJavaClassTemplate() ? IdeBundle.message("title.cannot.create.class") : IdeBundle.message("title.cannot.create.file"), Messages.getErrorIcon());
  }

  private static String filterMessage(String message){
    if (message == null) return null;
    @NonNls String ioExceptionPrefix = "java.io.IOException:";
    if (message.startsWith(ioExceptionPrefix)){
      message = message.substring(ioExceptionPrefix.length());
    }
    return message;
  }

  protected JComponent createCenterPanel(){
    myAttrPanel.ensureFitToScreen(200, 200);
    JPanel centerPanel = new JPanel(new GridBagLayout());
    centerPanel.add(myAttrComponent, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(2, 2, 2, 2), 0, 0));
    //centerPanel.setBorder(BorderFactory.createCompoundBorder(new TitledBorder("Attributes"), BorderFactory.createEmptyBorder(0, 4, 2, 4)));
    return centerPanel;
  }

  public JComponent getPreferredFocusedComponent(){
    return IdeFocusTraversalPolicy.getPreferredFocusedComponent(myAttrComponent);
  }
}

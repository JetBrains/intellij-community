package com.intellij.ide.actions;

import com.intellij.ide.fileTemplates.impl.AllFileTemplatesConfigurable;
import com.intellij.ide.fileTemplates.ui.ConfigureTemplatesDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.LwRootContainer;
import org.apache.tools.ant.filters.StringInputStream;
import org.jdom.Attribute;
import org.jdom.Document;

import java.io.CharArrayWriter;

public class SaveFileAsTemplateAction extends AnAction{
  public void actionPerformed(AnActionEvent e){
    DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    String fileText = (String)dataContext.getData(DataConstants.FILE_TEXT);
    VirtualFile file = (VirtualFile)dataContext.getData(DataConstants.VIRTUAL_FILE);
    String extension = file.getExtension();
    String nameWithoutExtension = file.getNameWithoutExtension();
    AllFileTemplatesConfigurable fileTemplateOptions = new AllFileTemplatesConfigurable();
    ConfigureTemplatesDialog dialog = new ConfigureTemplatesDialog(project, fileTemplateOptions);
    PsiFile psiFile = (PsiFile)dataContext.getData(DataConstants.PSI_FILE);
    if(psiFile instanceof PsiJavaFile){
      PsiJavaFile javaFile = (PsiJavaFile)psiFile;
      String packageName = javaFile.getPackageName();
      if ((packageName != null)&&(packageName.length() > 0)){
        fileText = StringUtil.replace(fileText, packageName, "${PACKAGE_NAME}");
      }
      PsiClass[] classes = javaFile.getClasses();
      PsiClass psiClass = null;
      if((classes != null) && (classes.length > 0)){
        for (int i = 0; i < classes.length; i++){
          PsiClass aClass = classes[i];
          if(nameWithoutExtension.equals(aClass.getName())){
            psiClass = aClass;
            break;
          }
        }
      }
      if(psiClass != null){
        //todo[myakovlev] ? PsiIdentifier nameIdentifier = psiClass.getNameIdentifier();
        fileText = StringUtil.replace(fileText, nameWithoutExtension,"${NAME}");
      }
    }
    else if (StdFileTypes.GUI_DESIGNER_FORM.equals(FileTypeManager.getInstance().getFileTypeByFile(file))) {
      LwRootContainer rootContainer = null;
      try {
        rootContainer = Utils.getRootContainer(fileText, null);
      }
      catch (Exception ignored) {
      }
      if (rootContainer != null && rootContainer.getClassToBind() != null) {
        try {
          Document document = JDOMUtil.loadDocument(new StringInputStream(fileText));

          Attribute attribute = document.getRootElement().getAttribute("bind-to-class");
          attribute.detach();

          CharArrayWriter writer = new CharArrayWriter();
          JDOMUtil.writeDocument(document, writer, CodeStyleSettingsManager.getSettings(project).getLineSeparator());

          fileText = writer.toString();
        }
        catch (Exception ignored) {
        }
      }
    }
    fileTemplateOptions.createNewTemplate(nameWithoutExtension, extension, fileText);
    dialog.show();
  }

  public void update(AnActionEvent e){
    DataContext dataContext = e.getDataContext();
    VirtualFile file = (VirtualFile)dataContext.getData(DataConstants.VIRTUAL_FILE);
    String fileText = (String)dataContext.getData(DataConstants.FILE_TEXT);
    e.getPresentation().setEnabled((fileText != null) && (file != null));
  }
}

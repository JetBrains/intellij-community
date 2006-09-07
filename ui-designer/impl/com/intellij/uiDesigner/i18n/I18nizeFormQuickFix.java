package com.intellij.uiDesigner.i18n;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInspection.i18n.I18nizeQuickFix;
import com.intellij.codeInspection.i18n.I18nizeQuickFixDialog;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.quickFixes.QuickFix;
import com.intellij.util.IncorrectOperationException;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiDocumentManager;

import java.util.Collection;

/**
 * @author yole
 */
public abstract class I18nizeFormQuickFix extends QuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.i18n.I18nizeFormQuickFix");

  public I18nizeFormQuickFix(final GuiEditor editor, final String name, final RadComponent component) {
    super(editor, name, component);
  }

  public void run() {
    final StringDescriptor descriptor = getStringDescriptorValue();
    final Project project = myEditor.getProject();

    PsiFile psiFile = PsiManager.getInstance(project).findFile(myEditor.getFile());
    final I18nizeQuickFixDialog dialog = new I18nizeQuickFixDialog(project, psiFile, null, descriptor.getValue(), false, false){
      protected String getDimensionServiceKey() {
        return "#com.intellij.codeInsight.i18n.I18nizeQuickFixDialog_Form";
      }
    };
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }

    if (!myEditor.ensureEditable()) {
      return;
    }
    final Collection<PropertiesFile> propertiesFiles = dialog.getAllPropertiesFiles();
    PropertiesFile aPropertiesFile = null;
    for (PropertiesFile file : propertiesFiles) {
      if (!CodeInsightUtil.prepareFileForWrite(file)) return;
      if (aPropertiesFile == null) {
        aPropertiesFile = file;
      }
    }

    CommandProcessor.getInstance().executeCommand(project, new Runnable(){
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable(){
          public void run() {
            try {
              I18nizeQuickFix.createProperty(project, propertiesFiles, dialog.getKey(), dialog.getValue());
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        });
      }
    }, CodeInsightBundle.message("quickfix.i18n.command.name"),project);

    // saving files is necessary to ensure correct reload of properties files by UI Designer
    for(PropertiesFile file: propertiesFiles) {
      FileDocumentManager.getInstance().saveDocument(PsiDocumentManager.getInstance(project).getDocument(file));
    }

    if (aPropertiesFile != null) {
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      String packageName = fileIndex.getPackageNameByDirectory(aPropertiesFile.getVirtualFile().getParent());
      if (packageName != null) {
        String bundleName;
        if (packageName.length() > 0) {
          bundleName = packageName + "." + aPropertiesFile.getResourceBundle().getBaseName();
        }
        else {
          bundleName = aPropertiesFile.getResourceBundle().getBaseName();
        }
        bundleName = bundleName.replace('.', '/');
        try {
          setStringDescriptorValue(new StringDescriptor(bundleName, dialog.getKey()));
        }
        catch (Exception e) {
          LOG.error(e);
        }
        myEditor.refreshAndSave(true);
      }
    }
  }

  protected abstract StringDescriptor getStringDescriptorValue();
  protected abstract void setStringDescriptorValue(final StringDescriptor descriptor) throws Exception;
}

package com.intellij.codeInsight.i18n;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiManager;
import com.intellij.uiDesigner.GuiEditor;
import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.quickFixes.QuickFix;
import com.intellij.util.IncorrectOperationException;

import java.util.Collection;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 12.08.2005
 * Time: 18:34:04
 * To change this template use File | Settings | File Templates.
 */
public abstract class I18nizeFormQuickFix extends QuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.i18n.I18nizeFormQuickFix");
  protected final RadComponent myComponent;

  public I18nizeFormQuickFix(final GuiEditor editor, final String name, final RadComponent component) {
    super(editor, name);
    myComponent = component;
  }

  public void run() {
    final StringDescriptor descriptor = getStringDescriptorValue();
    final Project project = myEditor.getProject();
    final PsiManager psiManager = PsiManager.getInstance(project);
    final PsiFile formPsiFile = psiManager.findFile(myEditor.getFile());
    PsiLiteralExpression expr;
    try {
      final PsiElementFactory elementFactory = psiManager.getElementFactory();
      expr = (PsiLiteralExpression) elementFactory.createExpressionFromText("\"" +
                                                                            StringUtil.escapeStringCharacters(descriptor.getValue()) +
                                                                            "\"", formPsiFile);
    }
    catch (IncorrectOperationException e) {
      return;
    }

    final I18nizeQuickFixDialog dlg = new I18nizeQuickFixDialog(project, expr, false);
    dlg.show();
    if (!dlg.isOK()) {
      return;
    }

    if (!myEditor.ensureEditable()) {
      return;
    }
    final Collection<PropertiesFile> propertiesFiles = dlg.getAllPropertiesFiles(project);
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
              I18nizeQuickFix.createProperty(project, dlg, propertiesFiles);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        });
      }
    }, CodeInsightBundle.message("quickfix.i18n.command.name"),project);

    if (aPropertiesFile != null) {
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      String packageName = fileIndex.getPackageNameByDirectory(aPropertiesFile.getVirtualFile().getParent());
      if (packageName != null) {
        String bundleName = packageName + "." + aPropertiesFile.getResourceBundle().getBaseName();
        bundleName = bundleName.replace('.', '/');
        try {
          setStringDescriptorValue(new StringDescriptor(bundleName, dlg.getKey()));
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

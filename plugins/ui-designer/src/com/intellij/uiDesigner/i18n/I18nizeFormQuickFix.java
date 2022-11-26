// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.i18n;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.i18n.JavaI18nizeQuickFixDialog;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.references.I18nUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.quickFixes.QuickFix;
import com.intellij.util.IncorrectOperationException;

import java.util.Collection;


public class I18nizeFormQuickFix extends QuickFix {
  private static final Logger LOG = Logger.getInstance(I18nizeFormQuickFix.class);
  private final StringDescriptorAccessor myAccessor;

  I18nizeFormQuickFix(final GuiEditor editor, final @IntentionName String name, StringDescriptorAccessor accessor) {
    super(editor, name, accessor.getComponent());
    myAccessor = accessor;
  }

  @Override
  public void run() {
    final StringDescriptor descriptor = getStringDescriptorValue();
    final Project project = myEditor.getProject();

    PsiFile psiFile = myEditor.getPsiFile();

    if (!JavaI18nizeQuickFixDialog.isAvailable(myEditor.getPsiFile())) {
      return;
    }
    String initialValue = StringUtil.escapeStringCharacters(descriptor.getValue());
    final JavaI18nizeQuickFixDialog<?> dialog = new JavaI18nizeQuickFixDialog<>(project, psiFile, null, initialValue, null, false, false) {
      @Override
      protected String getDimensionServiceKey() {
        return "#com.intellij.codeInsight.i18n.I18nizeQuickFixDialog_Form";
      }
    };
    if (!dialog.showAndGet()) {
      return;
    }

    if (!myEditor.ensureEditable()) {
      return;
    }
    final Collection<PropertiesFile> propertiesFiles = dialog.getAllPropertiesFiles();
    PropertiesFile aPropertiesFile = null;
    for (PropertiesFile file : propertiesFiles) {
      if (!FileModificationService.getInstance().prepareFileForWrite(file.getContainingFile())) return;
      if (aPropertiesFile == null) {
        aPropertiesFile = file;
      }
    }

    CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        I18nUtil.createProperty(project, propertiesFiles, dialog.getKey(), dialog.getValue());
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }), PropertiesBundle.message("quickfix.i18n.command.name"), project);

    // saving files is necessary to ensure correct reload of properties files by UI Designer
    for (PropertiesFile file : propertiesFiles) {
      FileDocumentManager.getInstance().saveDocument(PsiDocumentManager.getInstance(project).getDocument(file.getContainingFile()));
    }

    if (aPropertiesFile != null) {
      String bundleName = getBundleName(project, aPropertiesFile);
      if (bundleName != null){
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

  static String getBundleName(Project project, PropertiesFile aPropertiesFile) {
    String packageName = PackageIndex.getInstance(project).getPackageNameByDirectory(aPropertiesFile.getVirtualFile().getParent());
    if (packageName == null) return null;
    String bundleName;
    if (!packageName.isEmpty()) {
      bundleName = packageName + "." + aPropertiesFile.getResourceBundle().getBaseName();
    }
    else {
      bundleName = aPropertiesFile.getResourceBundle().getBaseName();
    }
    return bundleName.replace('.', '/');
  }

  protected StringDescriptor getStringDescriptorValue() {
    return myAccessor.getStringDescriptorValue();
  }

  protected void setStringDescriptorValue(final StringDescriptor descriptor) throws Exception {
    myAccessor.setStringDescriptorValue(descriptor);
  }
}

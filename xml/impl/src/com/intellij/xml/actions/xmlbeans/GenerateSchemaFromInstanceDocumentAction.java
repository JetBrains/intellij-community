// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.actions.xmlbeans;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.xml.XmlBundle;
import org.apache.xmlbeans.impl.inst2xsd.Inst2Xsd;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
final class GenerateSchemaFromInstanceDocumentAction extends AnAction {
  private static final NotNullLazyValue<Map<String, String>> DESIGN_TYPES = AtomicNotNullLazyValue.createValue(() -> {
    Map<String, String> result = new HashMap<>();
    result.put(GenerateSchemaFromInstanceDocumentDialog.LOCAL_ELEMENTS_GLOBAL_COMPLEX_TYPES, "vb");
    result.put(GenerateSchemaFromInstanceDocumentDialog.LOCAL_ELEMENTS_TYPES, "ss");
    result.put(GenerateSchemaFromInstanceDocumentDialog.GLOBAL_ELEMENTS_LOCAL_TYPES, "rd");
    return result;
  });

  private static final NotNullLazyValue<Map<String, String>> CONTENT_TYPES = AtomicNotNullLazyValue.createValue(() -> {
    Map<String, String> result = new HashMap<>();
    result.put(GenerateSchemaFromInstanceDocumentDialog.SMART_TYPE, "smart");
    result.put(GenerateSchemaFromInstanceDocumentDialog.STRING_TYPE, "string");
    return result;
  });

  @Override
  public void update(@NotNull AnActionEvent e) {
    final VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    final boolean enabled = isAcceptableFile(file);
    e.getPresentation().setEnabled(enabled);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      e.getPresentation().setVisible(enabled);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    final VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);

    final GenerateSchemaFromInstanceDocumentDialog dialog = new GenerateSchemaFromInstanceDocumentDialog(project, file);
    dialog.setOkAction(() -> doAction(project, dialog));

    dialog.show();
  }

  private static void doAction(final Project project, final GenerateSchemaFromInstanceDocumentDialog dialog) {
    FileDocumentManager.getInstance().saveAllDocuments();

    final String url = dialog.getUrl().getText();
    final VirtualFile relativeFile = VfsUtilCore.findRelativeFile(ExternalResourceManager.getInstance().getResourceLocation(url), null);
    VirtualFile relativeFileDir;
    if (relativeFile == null) {
      Messages.showErrorDialog(project, XmlBundle.message("file.doesnt.exist", url), XmlBundle.message("error"));
      return;
    } else {
      relativeFileDir = relativeFile.getParent();
    }
    if (relativeFileDir == null) {
      Messages.showErrorDialog(project, XmlBundle.message("file.doesnt.exist", url), XmlBundle.message("error"));
      return;
    }

    @NonNls List<String> parameters = new LinkedList<>();
    parameters.add("-design");
    parameters.add(DESIGN_TYPES.getValue().get(dialog.getDesignType()));

    parameters.add("-simple-content-types");
    parameters.add(CONTENT_TYPES.getValue().get(dialog.getSimpleContentType()));

    parameters.add("-enumerations");
    String enumLimit = dialog.getEnumerationsLimit();
    parameters.add("0".equals(enumLimit) ? "never" : enumLimit);

    parameters.add("-outDir");
    final String dirPath = relativeFileDir.getPath();
    parameters.add(dirPath);

    final File expectedSchemaFile = new File(dirPath + File.separator + relativeFile.getName() + "0.xsd");
    if (expectedSchemaFile.exists()) {
      if (!expectedSchemaFile.delete()) {
        Messages.showErrorDialog(project, XmlBundle.message("cant.delete.file", expectedSchemaFile.getPath()), XmlBundle.message("error"));
        return;
      }
    }

    parameters.add("-outPrefix");
    parameters.add(relativeFile.getName());

    parameters.add(url);
    File xsd = new File(dirPath + File.separator + dialog.getTargetSchemaName());
    final VirtualFile xsdFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(xsd);
    if (xsdFile != null) {
        ApplicationManager.getApplication().runWriteAction(() -> {
          try {
            xsdFile.delete(null);
          } catch (IOException e) {//
          }
        });
    }

    Inst2Xsd.main(ArrayUtilRt.toStringArray(parameters));
    if (expectedSchemaFile.exists()) {
      final boolean renamed = expectedSchemaFile.renameTo(xsd);
      if (! renamed) {
        Messages.showErrorDialog(project, XmlBundle.message("cant.rename.file", expectedSchemaFile.getPath(), xsd.getPath()), XmlBundle.message("error"));
      }
    }

    VirtualFile xsdVFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(xsd);
    if (xsdVFile != null) {
      FileEditorManager.getInstance(project).openFile(xsdVFile, true);
    } else {
      Messages.showErrorDialog(project, XmlBundle.message("xml2xsd.generator.error.message"), XmlBundle.message("xml2xsd.generator.error"));
    }

  }

  public static boolean isAcceptableFile(VirtualFile file) {
    return file != null && "xml".equalsIgnoreCase(file.getExtension());
  }
}

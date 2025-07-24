// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.tools;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import org.apache.xmlbeans.impl.inst2xsd.Inst2Xsd;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.security.Permission;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
final class GenerateSchemaFromInstanceDocumentAction extends AnAction {
  private static class Holder {
    private static final Map<String, String> DESIGN_TYPES = new HashMap<>();
    static {
      DESIGN_TYPES.put(GenerateSchemaFromInstanceDocumentDialog.getLocalElementsGlobalComplexTypes(), "vb");
      DESIGN_TYPES.put(GenerateSchemaFromInstanceDocumentDialog.getLocalElementsTypes(), "ss");
      DESIGN_TYPES.put(GenerateSchemaFromInstanceDocumentDialog.getGlobalElementsLocalTypes(), "rd");
    }

    private static final Map<String, String> CONTENT_TYPES = new HashMap<>();
    static {
      CONTENT_TYPES.put(GenerateSchemaFromInstanceDocumentDialog.SMART_TYPE, "smart");
      CONTENT_TYPES.put(GenerateSchemaFromInstanceDocumentDialog.STRING_TYPE, "string");
    }
  }
  @Override
  public void update(@NotNull AnActionEvent e) {
    final VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    final boolean enabled = isAcceptableFile(file);
    e.getPresentation().setEnabled(enabled);
    if (e.isFromContextMenu()) {
      e.getPresentation().setVisible(enabled);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
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
      Messages.showErrorDialog(project, XmlBeansBundle.message("file.doesnt.exist", url), XmlBeansBundle.message("error"));
      return;
    } else {
      relativeFileDir = relativeFile.getParent();
    }
    if (relativeFileDir == null) {
      Messages.showErrorDialog(project, XmlBeansBundle.message("file.doesnt.exist", url), XmlBeansBundle.message("error"));
      return;
    }

    @NonNls List<String> parameters = new LinkedList<>();
    parameters.add("-design");
    parameters.add(Holder.DESIGN_TYPES.get(dialog.getDesignType()));

    parameters.add("-simple-content-types");
    parameters.add(Holder.CONTENT_TYPES.get(dialog.getSimpleContentType()));

    parameters.add("-enumerations");
    String enumLimit = dialog.getEnumerationsLimit();
    parameters.add("0".equals(enumLimit) ? "never" : enumLimit);

    parameters.add("-outDir");
    final String dirPath = relativeFileDir.getPath();
    parameters.add(dirPath);

    final File expectedSchemaFile = new File(dirPath + File.separator + relativeFile.getName() + "0.xsd");
    if (expectedSchemaFile.exists()) {
      if (!expectedSchemaFile.delete()) {
        Messages.showErrorDialog(project, XmlBeansBundle.message("cant.delete.file", expectedSchemaFile.getPath()), XmlBeansBundle.message("error"));
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

    // Inst2Xsd.main contains exit() calls, so we need to prevent this
    SecurityManager old = System.getSecurityManager();
    try {
      System.setSecurityManager(new SecurityManager() {
        @Override
        public void checkExit(int status) {
          throw new SecurityException();
        }

        @Override
        public void checkPermission(Permission perm) {
        }
      });
      Inst2Xsd.main(ArrayUtilRt.toStringArray(parameters));
    }
    catch (Exception e) {
      Messages.showErrorDialog(project, XmlBeansBundle.message("xml2xsd.generator.error.message"), XmlBeansBundle.message("xml2xsd.generator.error"));
      return;
    }
    finally {
      System.setSecurityManager(old);
    }
    if (expectedSchemaFile.exists()) {
      final boolean renamed = expectedSchemaFile.renameTo(xsd);
      if (! renamed) {
        Messages.showErrorDialog(project, XmlBeansBundle.message("cant.rename.file", expectedSchemaFile.getPath(), xsd.getPath()), XmlBeansBundle.message("error"));
      }
    }

    VirtualFile xsdVFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(xsd);
    if (xsdVFile != null) {
      FileEditorManager.getInstance(project).openFile(xsdVFile, true);
    } else {
      Messages.showErrorDialog(project, XmlBeansBundle.message("xml2xsd.generator.error.message"), XmlBeansBundle.message("xml2xsd.generator.error"));
    }

  }

  public static boolean isAcceptableFile(VirtualFile file) {
    return file != null && "xml".equalsIgnoreCase(file.getExtension());
  }
}

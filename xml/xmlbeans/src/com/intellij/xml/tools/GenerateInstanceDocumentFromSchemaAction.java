// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.tools;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;


@ApiStatus.Internal
public final class GenerateInstanceDocumentFromSchemaAction extends AnAction {
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
    if (project == null || file == null) return;

    final GenerateInstanceDocumentFromSchemaDialog dialog = new GenerateInstanceDocumentFromSchemaDialog(project, file);
    dialog.setOkAction(() -> doAction(project, dialog));

    dialog.show();
  }

  public static void doAction(final Project project, final GenerateInstanceDocumentFromSchemaDialog dialog) {
    FileDocumentManager.getInstance().saveAllDocuments();

    @NonNls List<String> parameters = new LinkedList<>();

    final String url = dialog.getUrl().getText();
    final VirtualFile relativeFile = VfsUtilCore.findRelativeFile(ExternalResourceManager.getInstance().getResourceLocation(url), null);
    if (relativeFile == null) {
      Messages.showErrorDialog(project, XmlBeansBundle.message("file.doesnt.exist", url), XmlBeansBundle.message("error"));
      return;
    }
    final PsiFile file = PsiManager.getInstance(project).findFile(relativeFile);
    if (!(file instanceof XmlFile)) {
      Messages.showErrorDialog(project, " (" + file.getFileType().getDescription() + ")", XmlBeansBundle.message("error"));
      return;
    }

    VirtualFile relativeFileDir = relativeFile.getParent();
    if (relativeFileDir == null) {
      Messages.showErrorDialog(project, XmlBeansBundle.message("file.doesnt.exist", url), XmlBeansBundle.message("error"));
      return;
    }

    if (!dialog.enableRestrictionCheck()) {
      parameters.add("-nopvr");
    }

    if (!dialog.enableUniquenessCheck()) {
      parameters.add("-noupa");
    }
    parameters.add("-dl");

    String pathToUse;

    try {
      final File tempDir = FileUtil.createTempFile("xsd2inst", "");
      tempDir.delete();
      tempDir.mkdir();

      pathToUse = tempDir.getPath() + File.separatorChar + Xsd2InstanceUtils.processAndSaveAllSchemas(
        (XmlFile)file,
        new HashMap<>(),
        new Xsd2InstanceUtils.SchemaReferenceProcessor() {
          @Override
          public void processSchema(String schemaFileName, byte[] schemaContent) {
            try {
              final String fullFileName = tempDir.getPath() + File.separatorChar + schemaFileName;
              FileUtils.saveStreamContentAsFile(
                fullFileName,
                new ByteArrayInputStream(schemaContent)
              );
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        }
      );
    }
    catch (IOException e) {
      return;
    }

    parameters.add(pathToUse);

    parameters.add("-name");
    parameters.add(dialog.getElementName());

    String xml;
    try {
      xml = Xsd2InstanceUtils.generate(ArrayUtilRt.toStringArray(parameters));
    }
    catch (Throwable e) {
      Messages.showErrorDialog(project, ExceptionUtil.getMessage(e), XmlBeansBundle.message("error"));
      return;
    }

    String xmlFileName = relativeFileDir.getPath() + File.separatorChar + dialog.getOutputFileName();

    try {
      // the generated XML doesn't have any XML declaration -> utf-8
      final File xmlFile = new File(xmlFileName);
      FileUtil.writeToFile(xmlFile, xml);

      VirtualFile virtualFile =
        WriteAction.compute(() -> LocalFileSystem.getInstance().refreshAndFindFileByIoFile(xmlFile));
      FileEditorManager.getInstance(project).openFile(virtualFile, true);
    }
    catch (IOException e) {
      Messages.showErrorDialog(project, XmlBeansBundle.message("could.not.save.generated.xml.document.0", StringUtil.getMessage(e)),
                               XmlBeansBundle.message("error"));
    }
  }

  static boolean isAcceptableFileForGenerateSchemaFromInstanceDocument(VirtualFile virtualFile) {
    return virtualFile != null && "xsd".equalsIgnoreCase(virtualFile.getExtension());
  }

  public static boolean isAcceptableFile(VirtualFile file) {
    return isAcceptableFileForGenerateSchemaFromInstanceDocument(file);
  }
}

/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlBundle;
import org.apache.xmlbeans.impl.inst2xsd.Inst2Xsd;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public class GenerateSchemaFromInstanceDocumentAction extends AnAction {
  private static final Map<String, String> DESIGN_TYPES = new HashMap<>();
  private static final Map<String, String> CONTENT_TYPES = new HashMap<>();
  static {
    DESIGN_TYPES.put(GenerateSchemaFromInstanceDocumentDialog.LOCAL_ELEMENTS_GLOBAL_COMPLEX_TYPES, "vb");
    DESIGN_TYPES.put(GenerateSchemaFromInstanceDocumentDialog.LOCAL_ELEMENTS_TYPES, "ss");
    DESIGN_TYPES.put(GenerateSchemaFromInstanceDocumentDialog.GLOBAL_ELEMENTS_LOCAL_TYPES, "rd");
    CONTENT_TYPES.put(GenerateSchemaFromInstanceDocumentDialog.SMART_TYPE, "smart");
    CONTENT_TYPES.put(GenerateSchemaFromInstanceDocumentDialog.STRING_TYPE, "string");
  }

  //private static final
  
  @Override
  public void update(AnActionEvent e) {
    final VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
    final boolean enabled = isAcceptableFile(file);
    e.getPresentation().setEnabled(enabled);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      e.getPresentation().setVisible(enabled);
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    final VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());

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
    parameters.add(DESIGN_TYPES.get(dialog.getDesignType()));

    parameters.add("-simple-content-types");
    parameters.add(CONTENT_TYPES.get(dialog.getSimpleContentType()));

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

    Inst2Xsd.main(ArrayUtil.toStringArray(parameters));
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

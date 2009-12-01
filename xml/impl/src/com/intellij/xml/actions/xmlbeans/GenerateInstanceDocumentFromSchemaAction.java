/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlBundle;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringBufferInputStream;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class GenerateInstanceDocumentFromSchemaAction extends AnAction {
  @Override
  public void update(AnActionEvent e) {
    final VirtualFile file = PlatformDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
    final boolean enabled = isAcceptableFile(file);
    e.getPresentation().setEnabled(enabled);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      e.getPresentation().setVisible(enabled);
    }    
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    final VirtualFile file = PlatformDataKeys.VIRTUAL_FILE.getData(e.getDataContext());

    final GenerateInstanceDocumentFromSchemaDialog dialog = new GenerateInstanceDocumentFromSchemaDialog(project, file);
    dialog.setOkAction(new Runnable() {
      public void run() {
        doAction(project, dialog);
      }
    });

    dialog.show();
  }

  private void doAction(final Project project, final GenerateInstanceDocumentFromSchemaDialog dialog) {
    FileDocumentManager.getInstance().saveAllDocuments();

    @NonNls List<String> parameters = new LinkedList<String>();

    final String url = dialog.getUrl().getText();
    final VirtualFile relativeFile = VfsUtil.findRelativeFile(ExternalResourceManager.getInstance().getResourceLocation(url), null);
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

    if (!dialog.enableRestrictionCheck()) {
      parameters.add("-nopvr");
    }

    if (!dialog.enableUniquenessCheck()) {
      parameters.add("-noupa");
    }


    String pathToUse;

    try {
      final File tempDir = File.createTempFile("xsd2inst","");
      tempDir.delete();
      tempDir.mkdir();

      pathToUse = tempDir.getPath() + File.separatorChar + Xsd2InstanceUtils.processAndSaveAllSchemas(
        (XmlFile) PsiManager.getInstance(project).findFile(relativeFile),
        new THashMap<String, String>(),
        new Xsd2InstanceUtils.SchemaReferenceProcessor() {
          public void processSchema(String schemaFileName, String schemaContent) {
            try {
              final String fullFileName = tempDir.getPath() + File.separatorChar + schemaFileName;
              FileUtils.saveStreamContentAsFile(
                fullFileName,
                new StringBufferInputStream(schemaContent)
              );
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        }
      );
    } catch (IOException e) {
      return;
    }

    parameters.add(pathToUse);

    parameters.add("-name");
    parameters.add(dialog.getElementName());

    String xml;
    try {
      xml = Xsd2InstanceUtils.generate(ArrayUtil.toStringArray(parameters));
    } catch (IllegalArgumentException e) {
      Messages.showErrorDialog(project, StringUtil.getMessage(e), XmlBundle.message("error"));
      return;
    }



    final VirtualFile baseDirForCreatedInstanceDocument1 = relativeFileDir;
    String xmlFileName = baseDirForCreatedInstanceDocument1.getPath() + File.separatorChar + dialog.getOutputFileName();
    FileOutputStream fileOutputStream = null;

    try {
      fileOutputStream = new FileOutputStream(xmlFileName);
      fileOutputStream.write(xml.getBytes());
      fileOutputStream.close();
      fileOutputStream = null;
    }
    catch (IOException e) {
      e.printStackTrace();
    }

              final File xmlFile = new File(xmlFileName);
    VirtualFile virtualFile = ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
                @Nullable
                public VirtualFile compute() {
                  return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(xmlFile);
                }
              });
              FileEditorManager.getInstance(project).openFile(virtualFile, true);
  }

  static boolean isAcceptableFileForGenerateSchemaFromInstanceDocument(VirtualFile virtualFile) {
    return virtualFile != null && "xsd".equalsIgnoreCase(virtualFile.getExtension());
  }

  public static boolean isAcceptableFile(VirtualFile file) {
    return isAcceptableFileForGenerateSchemaFromInstanceDocument(file);
  }
}

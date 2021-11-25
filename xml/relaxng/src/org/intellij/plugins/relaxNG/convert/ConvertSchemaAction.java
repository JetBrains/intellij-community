/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.convert;

import com.intellij.ide.highlighter.DTDFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.intellij.plugins.relaxNG.RelaxNgMetaDataContributor;
import org.intellij.plugins.relaxNG.RelaxngBundle;
import org.intellij.plugins.relaxNG.compact.RncFileType;
import org.intellij.plugins.relaxNG.validation.RngValidateHandler;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class ConvertSchemaAction extends AnAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    final VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (files != null && project != null) {
      final SchemaType type = getInputType(project, files);
      e.getPresentation().setEnabled(type != null);
      if (type == SchemaType.XML) {
        e.getPresentation().setText(RelaxngBundle.message("relaxng.convert-schema.action.title.xml-files", files.length));
      } else {
        e.getPresentation().setText(RelaxngBundle.message("relaxng.convert-schema.action.title.non-xml"));
      }
    } else {
      e.getPresentation().setEnabled(false);
    }
  }

  private static SchemaType getInputType(Project project, VirtualFile... files) {
    if (files.length == 0) return null;

    final VirtualFile file = files[0];
    final FileType type = file.getFileType();
    if (type == XmlFileType.INSTANCE) {
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile instanceof XmlFile) {
        final XmlDocument document = ((XmlFile)psiFile).getDocument();
        if (document != null && document.getRootTag() != null) {
          final XmlTag rootTag = document.getRootTag();
          assert rootTag != null;
          final String uri = rootTag.getNamespace();
          if (RelaxNgMetaDataContributor.RNG_NAMESPACE.equals(uri) && files.length == 1) {
            return SchemaType.RNG;
          }
        }
      }
      if (files.length > 1) {
        for (VirtualFile virtualFile : files) {
          if (!FileTypeRegistry.getInstance().isFileOfType(virtualFile, XmlFileType.INSTANCE) || getInputType(project, virtualFile) != null) {
            return null;
          }
        }
      }
      return SchemaType.XML;
    } else if (type == DTDFileType.INSTANCE && files.length == 1) {
      return SchemaType.DTD;
    } else if (type == RncFileType.getInstance() && files.length == 1) {
      return SchemaType.RNC;
    }
    return null;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (file != null && project != null) {
      final VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
      assert files != null;

      final SchemaType type = getInputType(project, files);
      final ConvertSchemaDialog dialog = new ConvertSchemaDialog(project, type, file);
      if (!dialog.showAndGet()) {
        return;
      }

      RngValidateHandler.saveFiles(files);

      final ConvertSchemaSettings settings = dialog.getSettings();
      final IdeaErrorHandler errorHandler = new IdeaErrorHandler(project);
      new IdeaDriver(settings, project).convert(type, errorHandler, files);

      final VirtualFile output = LocalFileSystem.getInstance().findFileByIoFile(new File(settings.getOutputDestination()));
      if (output != null) {
        output.refresh(false, true);
      }
    }
  }
}
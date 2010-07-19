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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.intellij.plugins.relaxNG.ProjectLoader;
import org.intellij.plugins.relaxNG.compact.RncFileType;
import org.intellij.plugins.relaxNG.validation.ValidateAction;

import java.io.File;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 16.11.2007
*/
public class ConvertSchemaAction extends AnAction {

  public void update(AnActionEvent e) {
    final VirtualFile[] files = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (files != null && project != null) {
      final SchemaType type = getInputType(project, files);
      e.getPresentation().setEnabled(type != null);
      if (type == SchemaType.XML) {
        e.getPresentation().setText("Generate Schema from XML file" + (files.length > 1 ? "s" : "") + "...");
      } else {
        e.getPresentation().setText("Convert Schema...");
      }
    } else {
      e.getPresentation().setEnabled(false);
    }
  }

  private static SchemaType getInputType(Project project, VirtualFile... files) {
    if (files.length == 0) return null;

    final VirtualFile file = files[0];
    final FileType type = file.getFileType();
    if (type == StdFileTypes.XML) {
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile instanceof XmlFile) {
        final XmlDocument document = ((XmlFile)psiFile).getDocument();
        if (document != null && document.getRootTag() != null) {
          final XmlTag rootTag = document.getRootTag();
          assert rootTag != null;
          final String uri = rootTag.getNamespace();
          if (ProjectLoader.RNG_NAMESPACE.equals(uri) && files.length == 1) {
            return SchemaType.RNG;
          }
        }
      }
      if (files.length > 1) {
        for (VirtualFile virtualFile : files) {
          if (virtualFile.getFileType() != StdFileTypes.XML || getInputType(project, virtualFile) != null) {
            return null;
          }
        }
      }
      return SchemaType.XML;
    } else if (type == StdFileTypes.DTD && files.length == 1) {
      return SchemaType.DTD;
    } else if (type == RncFileType.getInstance() && files.length == 1) {
      return SchemaType.RNC;
    }
    return null;
  }

  public void actionPerformed(AnActionEvent e) {
    final VirtualFile file = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (file != null && project != null) {
      final VirtualFile[] files = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
      assert files != null;

      final SchemaType type = getInputType(project, files);
      final ConvertSchemaDialog dialog = new ConvertSchemaDialog(project, type, file);
      dialog.show();
      if (!dialog.isOK()) {
        return;
      }

      ValidateAction.saveFiles(files);

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
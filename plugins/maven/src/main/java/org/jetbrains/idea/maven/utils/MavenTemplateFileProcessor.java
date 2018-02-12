/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.ide.util.projectWizard.ProjectTemplateFileProcessor;
import com.intellij.ide.util.projectWizard.ProjectTemplateParameterFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.model.MavenConstants;

import java.io.IOException;

/**
 * @author Dmitry Avdeev
 */
public class MavenTemplateFileProcessor extends ProjectTemplateFileProcessor {

  @Nullable
  @Override
  protected String encodeFileText(final String content, final VirtualFile file, final Project project) throws IOException {
    if (MavenConstants.POM_XML.equals(file.getName())) {
      return ApplicationManager.getApplication().runReadAction(new ThrowableComputable<String, IOException>() {
        @Override
        public String compute() throws IOException {
          PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText(MavenConstants.POM_XML, XmlFileType.INSTANCE, content);
          final MavenDomProjectModel model = MavenDomUtil.getMavenDomModel(psiFile, MavenDomProjectModel.class);
          if (model == null) return null;
          String text = psiFile.getText();
          XmlElement element = model.getName().getXmlElement();
          if (element instanceof XmlTag) {
            text = ((XmlTag)element).getValue().getTextRange().replace(text, wrap(ProjectTemplateParameterFactory.IJ_PROJECT_NAME));
          }
          element = model.getArtifactId().getXmlElement();
          if (element instanceof XmlTag) {
            text = ((XmlTag)element).getValue().getTextRange().replace(text, wrap(ProjectTemplateParameterFactory.IJ_PROJECT_NAME));
          }
          return text;
        }
      });
    }
    return null;
  }
}

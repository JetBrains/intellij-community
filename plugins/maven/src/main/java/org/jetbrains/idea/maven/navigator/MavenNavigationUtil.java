/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.navigator;

import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.project.MavenArtifact;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;

import java.io.File;
import java.io.IOException;

/**
 * @author Konstantin Bulenkov
 */
public class MavenNavigationUtil {
  private MavenNavigationUtil() {
  }

  @Nullable
  public static Navigatable createNavigatableForPom(final Project project, final VirtualFile file) {
    if (file == null || !file.isValid()) return null;
    final PsiFile result = PsiManager.getInstance(project).findFile(file);
    return result == null ? null : new Navigatable.Adapter() {
      public void navigate(boolean requestFocus) {
        int offset = 0;
        if (result instanceof XmlFile) {
          final XmlDocument xml = ((XmlFile)result).getDocument();
          if (xml != null) {
            final XmlTag rootTag = xml.getRootTag();
            if (rootTag != null) {
              final XmlTag[] id = rootTag.findSubTags("artifactId", rootTag.getNamespace());
              if (id.length > 0) {
                offset = id[0].getValue().getTextRange().getStartOffset();
              }
            }
          }
        }
        new OpenFileDescriptor(project, file, offset).navigate(requestFocus);
      }
    };    
  }

  public static Navigatable createNavigatableForDependency(final Project project, MavenProject mavenProject, final MavenArtifact artifact) {
    final MavenProjectsManager myProjectsManager = MavenProjectsManager.getInstance(project);
    final Module m = myProjectsManager.findModule(mavenProject);
    if (m == null) return null;
    final File pom = MavenArtifactUtil.getArtifactFile(myProjectsManager.getLocalRepository(), artifact.getMavenId());
    final VirtualFile vPom;
    if (pom.exists()) {
     vPom = LocalFileSystem.getInstance().findFileByIoFile(pom);
    } else {
      final MavenProject mp = myProjectsManager.findProject(artifact);
      vPom = mp == null ? null : mp.getFile();
    }
    if (vPom != null) {
      return new Navigatable.Adapter() {
        public void navigate(boolean requestFocus) {
          int offset = 0;
          try {
            int index = new String(vPom.contentsToByteArray()).indexOf("<artifactId>" + artifact.getArtifactId() + "</artifactId>");
            if (index != -1) {
              offset += index + 12;
            }
          }
          catch (IOException e) {//
          }
          new OpenFileDescriptor(project, vPom, offset).navigate(requestFocus);
        }
      };
    }
    final OrderEntry e = MavenRootModelAdapter.findLibraryEntry(m, artifact);
    if (e == null) return null;
    return new Navigatable.Adapter() {
      public void navigate(boolean requestFocus) {
        ProjectSettingsService.getInstance(project).openProjectLibrarySettings(new NamedLibraryElement(m, e));
      }
    };

  }
}

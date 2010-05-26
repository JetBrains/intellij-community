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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenArtifact;
import org.jetbrains.idea.maven.project.MavenId;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;

import java.io.File;

/**
 * @author Konstantin Bulenkov
 */
public class MavenNavigationUtil {
  private static final String DEPENDENCIES = "dependencies";
  private static final String ARTIFACT_ID = "artifactId";
  private static final String DEPENDENCY = "dependency";

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
              final XmlTag[] id = rootTag.findSubTags(ARTIFACT_ID, rootTag.getNamespace());
              if (id.length > 0) {
                offset = id[0].getValue().getTextRange().getStartOffset();
              }
            }
          }
        }
        navigate(project, file, offset, requestFocus);
      }
    };    
  }

  @Nullable
  public static Navigatable createNavigatableForDependency(final Project project, final VirtualFile file, final MavenArtifact artifact) {
    return new Navigatable.Adapter() {
      public void navigate(boolean requestFocus) {
        final PsiFile pom = PsiManager.getInstance(project).findFile(file);
        if (pom instanceof XmlFile) {
          final XmlTag id = findDependency((XmlFile)pom, artifact.getArtifactId());
          if (id != null) {
            navigate(project, file, id.getTextOffset() + id.getName().length() + 2, requestFocus);
          }
        }
      }
    };
    //final File pom = MavenArtifactUtil.getArtifactFile(myProjectsManager.getLocalRepository(), artifact.getMavenId());
    //final VirtualFile vPom;
    //if (pom.exists()) {
    //vPom = LocalFileSystem.getInstance().findFileByIoFile(pom);
    //} else {
    //  final MavenProject mp = myProjectsManager.findProject(artifact);
    //  vPom = mp == null ? null : mp.getFile();
    //}
    //if (vPom != null) {
    //  return new Navigatable.Adapter() {
    //    public void navigate(boolean requestFocus) {
    //      int offset = 0;
    //      try {
    //        int index = new String(vPom.contentsToByteArray()).indexOf("<artifactId>" + artifact.getArtifactId() + "</artifactId>");
    //        if (index != -1) {
    //          offset += index + 12;
    //        }
    //      }
    //      catch (IOException e) {//
    //      }
    //      new OpenFileDescriptor(project, vPom, offset).navigate(requestFocus);
    //    }
    //  };
    //}
    //
    //final Module m = myProjectsManager.findModule(mavenProject);
    //if (m == null) return null;
    //final OrderEntry e = MavenRootModelAdapter.findLibraryEntry(m, artifact);
    //if (e == null) return null;
    //return new Navigatable.Adapter() {
    //  public void navigate(boolean requestFocus) {
    //    ProjectSettingsService.getInstance(project).openProjectLibrarySettings(new NamedLibraryElement(m, e));
    //  }
    //};

  }

  @Nullable
  public static VirtualFile getArtifactFile(Project project, MavenId id) {
    final File file = MavenArtifactUtil.getArtifactFile(MavenProjectsManager.getInstance(project).getLocalRepository(), id);
    return file.exists() ? LocalFileSystem.getInstance().findFileByIoFile(file) : null;
  }

  @Nullable
  public static XmlTag findArtifactId(XmlFile xml, String artifactId) {
    final XmlDocument document = xml.getDocument();
    if (document != null) {
      final XmlTag root = document.getRootTag();
      if (root != null) {
        final XmlTag[] tags = root.findSubTags(ARTIFACT_ID, root.getNamespace());
        if (tags.length > 0) {
          return tags[0];
        }
      }
    }
    return null;
  }

  @Nullable
  public static XmlTag findDependency(XmlFile xml, String artifactId) {
    final XmlDocument document = xml.getDocument();
    if (document != null) {
      final XmlTag root = document.getRootTag();
      if (root != null) {
        final String namespace = root.getNamespace();
        final XmlTag[] tags = root.findSubTags(DEPENDENCIES, namespace);
        if (tags.length > 0) {
          for (XmlTag dep : tags[0].findSubTags(DEPENDENCY, namespace)) {
            final XmlTag[] ids = dep.findSubTags(ARTIFACT_ID, root.getNamespace());
            if (ids.length > 0 && artifactId.equals(ids[0].getValue().getTrimmedText())) {
              return ids[0];
            }
          }
        }
      }
    }
    return null;
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.navigator;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.pom.NavigatableAdapter;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependencies;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;

import java.io.File;
import java.util.Objects;

/**
 * @author Konstantin Bulenkov
 */
public class MavenNavigationUtil {
  private static final String ARTIFACT_ID = "artifactId";

  private MavenNavigationUtil() { }

  @Nullable
  public static Navigatable createNavigatableForPom(final Project project, final VirtualFile file) {
    if (file == null || !file.isValid()) return null;
    final PsiFile result = PsiManager.getInstance(project).findFile(file);
    return result == null ? null : new NavigatableAdapter() {
      @Override
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
    return new NavigatableAdapter() {
      @Override
      public void navigate(boolean requestFocus) {
        if (!file.isValid()) return;

        MavenDomProjectModel projectModel = MavenDomUtil.getMavenDomProjectModel(project, file);
        if (projectModel == null) return;

        MavenDomDependency dependency = findDependency(projectModel, artifact.getGroupId(), artifact.getArtifactId());
        if (dependency == null) return;

        XmlTag artifactId = dependency.getArtifactId().getXmlTag();
        if (artifactId == null) return;

        navigate(project, artifactId.getContainingFile().getVirtualFile(), artifactId.getTextOffset() + artifactId.getName().length() + 2, requestFocus);
      }
    };
  }

  @Nullable
  public static VirtualFile getArtifactFile(Project project, MavenId id) {
    final File file = MavenArtifactUtil.getArtifactFile(MavenProjectsManager.getInstance(project).getLocalRepository(), id);
    return file.exists() ? LocalFileSystem.getInstance().findFileByIoFile(file) : null;
  }

  @Nullable
  public static MavenDomDependency findDependency(@NotNull MavenDomProjectModel projectDom, final String groupId, final String artifactId) {
    MavenDomProjectProcessorUtils.SearchProcessor<MavenDomDependency, MavenDomDependencies> processor = new MavenDomProjectProcessorUtils.SearchProcessor<MavenDomDependency, MavenDomDependencies>() {
      @Nullable
      @Override
      protected MavenDomDependency find(MavenDomDependencies element) {
        for (MavenDomDependency dependency : element.getDependencies()) {
          if (Objects.equals(groupId, dependency.getGroupId().getStringValue()) &&
              Objects.equals(artifactId, dependency.getArtifactId().getStringValue())) {
            return dependency;
          }
        }

        return null;
      }
    };

    MavenDomProjectProcessorUtils.processDependencies(projectDom, processor);

    return processor.getResult();
  }
}

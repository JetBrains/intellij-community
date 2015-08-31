/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.openapi.roots.ProjectModelModifier;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.text.VersionComparatorUtil;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.library.RepositoryAttachHandler;

import java.util.*;

/**
 * @author nik
 */
public class MavenProjectModelModifier extends ProjectModelModifier {
  private final Project myProject;
  private final MavenProjectsManager myProjectsManager;
  private MavenProjectIndicesManager myIndicesManager;

  public MavenProjectModelModifier(Project project, MavenProjectsManager projectsManager, MavenProjectIndicesManager manager) {
    myProject = project;
    myProjectsManager = projectsManager;
    myIndicesManager = manager;
  }

  @Nullable
  @Override
  public Promise<Void> addModuleDependency(@NotNull Module from, @NotNull Module to, @NotNull final DependencyScope scope) {
    final MavenProject toProject = myProjectsManager.findProject(to);
    if (toProject == null) return null;
    MavenId mavenId = toProject.getMavenId();

    return addDependency(Collections.singletonList(from), mavenId, scope);
  }

  private Promise<Void> addDependency(@NotNull Collection<Module> fromModules, @NotNull final MavenId mavenId, @NotNull final DependencyScope scope) {
    final List<MavenDomProjectModel> models = new ArrayList<MavenDomProjectModel>(fromModules.size());
    List<XmlFile> files = new ArrayList<XmlFile>(fromModules.size());
    List<MavenProject> projectToUpdate = new ArrayList<MavenProject>(fromModules.size());
    for (Module from : fromModules) {
      if (!myProjectsManager.isMavenizedModule(from)) return null;
      MavenProject fromProject = myProjectsManager.findProject(from);
      if (fromProject == null) return null;

      final MavenDomProjectModel model = MavenDomUtil.getMavenDomProjectModel(myProject, fromProject.getFile());
      if (model == null) return null;
      models.add(model);
      files.add(DomUtil.getFile(model));
      projectToUpdate.add(fromProject);
    }

    new WriteCommandAction(myProject, "Add Maven Dependency", PsiUtilCore.toPsiFileArray(files)) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        for (MavenDomProjectModel model : models) {
          MavenDomDependency dependency = MavenDomUtil.createDomDependency(model, null, mavenId);
          String mavenScope = getMavenScope(scope);
          if (mavenScope != null) {
            dependency.getScope().setStringValue(mavenScope);
          }
          Document document = PsiDocumentManager.getInstance(myProject).getDocument(DomUtil.getFile(model));
          if (document != null) {
            FileDocumentManager.getInstance().saveDocument(document);
          }
        }
      }
    }.execute();
    return myProjectsManager.forceUpdateProjects(projectToUpdate);
  }

  @Nullable
  @Override
  public Promise<Void> addExternalLibraryDependency(@NotNull Collection<Module> modules,
                                                    @NotNull ExternalLibraryDescriptor descriptor,
                                                    @NotNull DependencyScope scope) {
    for (Module module : modules) {
      if (!myProjectsManager.isMavenizedModule(module)) {
        return null;
      }
    }

    String version = selectVersion(descriptor);
    MavenId mavenId = new MavenId(descriptor.getLibraryGroupId(), descriptor.getLibraryArtifactId(), version);
    return addDependency(modules, mavenId, scope);
  }

  @NotNull
  private String selectVersion(@NotNull ExternalLibraryDescriptor descriptor) {
    Set<String> versions = myIndicesManager.getVersions(descriptor.getLibraryGroupId(), descriptor.getLibraryArtifactId());
    List<String> suitableVersions = new ArrayList<String>();
    String minVersion = descriptor.getMinVersion();
    String maxVersion = descriptor.getMaxVersion();
    for (String version : versions) {
      if ((minVersion == null || VersionComparatorUtil.compare(minVersion, version) <= 0)
          && (maxVersion == null || VersionComparatorUtil.compare(version, maxVersion) < 0)) {
        suitableVersions.add(version);
      }
    }
    return Collections.max(suitableVersions, VersionComparatorUtil.COMPARATOR);
  }

  @Nullable
  @Override
  public Promise<Void> addLibraryDependency(@NotNull Module from, @NotNull Library library, @NotNull DependencyScope scope) {
    String name = library.getName();
    if (name != null && name.startsWith(MavenArtifact.MAVEN_LIB_PREFIX)) {
      //it would be better to use RepositoryLibraryType for libraries imported from Maven and fetch mavenId from the library properties instead
      String mavenCoordinates = StringUtil.trimStart(name, MavenArtifact.MAVEN_LIB_PREFIX);
      return addDependency(Collections.singletonList(from), RepositoryAttachHandler.getMavenId(mavenCoordinates), scope);
    }
    return null;
  }

  @Nullable
  private static String getMavenScope(DependencyScope scope) {
    switch (scope) {
      case RUNTIME:
        return MavenConstants.SCOPE_RUNTIME;
      case TEST:
        return MavenConstants.SCOPE_TEST;
      case PROVIDED:
        return MavenConstants.SCOPE_PROVIDED;
      default:
        return null;
    }
  }
}

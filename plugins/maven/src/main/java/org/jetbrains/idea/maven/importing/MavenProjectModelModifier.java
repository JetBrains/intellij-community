/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.roots.JavaProjectModelModifier;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.text.VersionComparatorUtil;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil;
import org.jetbrains.idea.maven.dom.model.*;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.*;

/**
 * @author nik
 */
public class MavenProjectModelModifier extends JavaProjectModelModifier {
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
  public Promise<Void> addModuleDependency(@NotNull Module from, @NotNull Module to, @NotNull DependencyScope scope, boolean exported) {
    final MavenProject toProject = myProjectsManager.findProject(to);
    if (toProject == null) return null;
    MavenId mavenId = toProject.getMavenId();
    return addDependency(Collections.singletonList(from), mavenId, scope);
  }

  private Promise<Void> addDependency(@NotNull Collection<Module> fromModules,
                                      @NotNull final MavenId mavenId,
                                      @NotNull final DependencyScope scope) {
    return addDependency(fromModules, mavenId, null, null, scope);
  }

  private Promise<Void> addDependency(@NotNull Collection<Module> fromModules,
                                      @NotNull final MavenId mavenId,
                                      @Nullable String minVersion,
                                      @Nullable String maxVersion,
                                      @NotNull final DependencyScope scope) {
    final List<Trinity<MavenDomProjectModel, MavenId, String>> models = new ArrayList<>(fromModules.size());
    List<XmlFile> files = new ArrayList<>(fromModules.size());
    List<MavenProject> projectToUpdate = new ArrayList<>(fromModules.size());
    final String mavenScope = getMavenScope(scope);
    for (Module from : fromModules) {
      if (!myProjectsManager.isMavenizedModule(from)) return null;
      MavenProject fromProject = myProjectsManager.findProject(from);
      if (fromProject == null) return null;

      final MavenDomProjectModel model = MavenDomUtil.getMavenDomProjectModel(myProject, fromProject.getFile());
      if (model == null) return null;

      String scopeToSet = null;
      String version = null;
      if (mavenId.getGroupId() != null && mavenId.getArtifactId() != null) {
        MavenDomDependency managedDependency =
          MavenDependencyCompletionUtil.findManagedDependency(model, myProject, mavenId.getGroupId(), mavenId.getArtifactId());
        if (managedDependency != null) {
          String managedScope = StringUtil.nullize(managedDependency.getScope().getStringValue(), true);
          scopeToSet = (managedScope == null && MavenConstants.SCOPE_COMPILE.equals(mavenScope)) ||
                       StringUtil.equals(managedScope, mavenScope)
                       ? null : mavenScope;
        }

        if (managedDependency == null || StringUtil.isEmpty(managedDependency.getVersion().getStringValue())) {
          version = selectVersion(mavenId, minVersion, maxVersion);
          scopeToSet = mavenScope;
        }
      }

      models.add(Trinity.create(model, new MavenId(mavenId.getGroupId(), mavenId.getArtifactId(), version), scopeToSet));
      files.add(DomUtil.getFile(model));
      projectToUpdate.add(fromProject);
    }

    new WriteCommandAction(myProject, "Add Maven Dependency", PsiUtilCore.toPsiFileArray(files)) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        for (Trinity<MavenDomProjectModel, MavenId, String> trinity : models) {
          final MavenDomProjectModel model = trinity.first;
          MavenDomDependency dependency = MavenDomUtil.createDomDependency(model, null, trinity.second);
          String mavenScope = trinity.third;
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

    MavenId mavenId = new MavenId(descriptor.getLibraryGroupId(), descriptor.getLibraryArtifactId(), null);
    return addDependency(modules, mavenId, descriptor.getMinVersion(), descriptor.getMaxVersion(), scope);
  }

  @NotNull
  private String selectVersion(@NotNull MavenId mavenId, @Nullable String minVersion, @Nullable String maxVersion) {
    Set<String> versions = myIndicesManager.getVersions(mavenId.getGroupId(), mavenId.getArtifactId());
    List<String> suitableVersions = new ArrayList<>();
    for (String version : versions) {
      if ((minVersion == null || VersionComparatorUtil.compare(minVersion, version) <= 0)
          && (maxVersion == null || VersionComparatorUtil.compare(version, maxVersion) <= 0)) {
        suitableVersions.add(version);
      }
    }
    return suitableVersions.isEmpty() ? "RELEASE" : Collections.max(suitableVersions, VersionComparatorUtil.COMPARATOR);
  }

  @Nullable
  @Override
  public Promise<Void> addLibraryDependency(@NotNull Module from, @NotNull Library library, @NotNull DependencyScope scope, boolean exported) {
    String name = library.getName();
    if (name != null && name.startsWith(MavenArtifact.MAVEN_LIB_PREFIX)) {
      //it would be better to use RepositoryLibraryType for libraries imported from Maven and fetch mavenId from the library properties instead
      String mavenCoordinates = StringUtil.trimStart(name, MavenArtifact.MAVEN_LIB_PREFIX);
      return addDependency(Collections.singletonList(from), new MavenId(mavenCoordinates), scope);
    }
    return null;
  }

  @Override
  public Promise<Void> changeLanguageLevel(@NotNull Module module, @NotNull final LanguageLevel level) {
    if (!myProjectsManager.isMavenizedModule(module)) return null;

    MavenProject mavenProject = myProjectsManager.findProject(module);
    if (mavenProject == null) return null;

    final MavenDomProjectModel model = MavenDomUtil.getMavenDomProjectModel(myProject, mavenProject.getFile());
    if (model == null) return null;

    new WriteCommandAction(myProject, "Add Maven Dependency", DomUtil.getFile(model)) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        MavenDomConfiguration configuration = getCompilerPlugin(model).getConfiguration();
        XmlTag tag = configuration.ensureTagExists();
        setChildTagValue(tag, "source", level.getCompilerComplianceDefaultOption());
        setChildTagValue(tag, "target", level.getCompilerComplianceDefaultOption());
        Document document = PsiDocumentManager.getInstance(myProject).getDocument(DomUtil.getFile(model));
        if (document != null) {
          FileDocumentManager.getInstance().saveDocument(document);
        }
      }
    }.execute();
    return myProjectsManager.forceUpdateProjects(Collections.singleton(mavenProject));
  }

  private static void setChildTagValue(@NotNull XmlTag tag, @NotNull String subTagName, @NotNull String value) {
    XmlTag subTag = tag.findFirstSubTag(subTagName);
    if (subTag != null) {
      subTag.getValue().setText(value);
    }
    else {
      tag.addSubTag(tag.createChildTag(subTagName, tag.getNamespace(), value, false), false);
    }
  }

  @NotNull
  private static MavenDomPlugin getCompilerPlugin(MavenDomProjectModel model) {
    MavenDomPlugins plugins = model.getBuild().getPlugins();
    for (MavenDomPlugin plugin : plugins.getPlugins()) {
      if ("org.apache.maven.plugins".equals(plugin.getGroupId().getValue()) &&
          "maven-compiler-plugin".equals(plugin.getArtifactId().getValue())) {
        return plugin;
      }
    }
    MavenDomPlugin plugin = plugins.addPlugin();
    plugin.getGroupId().setValue("org.apache.maven.plugins");
    plugin.getArtifactId().setValue("maven-compiler-plugin");
    return plugin;
  }

  @Nullable
  private static String getMavenScope(@NotNull DependencyScope scope) {
    switch (scope) {
      case RUNTIME:
        return MavenConstants.SCOPE_RUNTIME;
      case COMPILE:
        return MavenConstants.SCOPE_COMPILE;
      case TEST:
        return MavenConstants.SCOPE_TEST;
      case PROVIDED:
        return MavenConstants.SCOPE_PROVIDED;
      default:
        return null;
    }
  }
}

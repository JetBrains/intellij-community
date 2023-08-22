// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

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
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomPlugin;
import org.jetbrains.idea.maven.dom.model.MavenDomPlugins;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.reposearch.DependencySearchService;
import org.jetbrains.jps.model.java.JpsJavaSdkType;

import java.util.*;

public final class MavenProjectModelModifier extends JavaProjectModelModifier {
  private final Project myProject;
  private final MavenProjectsManager myProjectsManager;

  public MavenProjectModelModifier(Project project) {
    myProject = project;
    myProjectsManager = MavenProjectsManager.getInstance(project);
  }

  @Nullable
  @Override
  public Promise<Void> addModuleDependency(@NotNull Module from, @NotNull Module to, @NotNull DependencyScope scope, boolean exported) {
    final MavenProject toProject = myProjectsManager.findProject(to);
    if (toProject == null) return null;
    MavenId mavenId = toProject.getMavenId();
    return addDependency(Collections.singletonList(from), mavenId, scope);
  }

  private Promise<Void> addDependency(@NotNull Collection<? extends Module> fromModules,
                                      @NotNull final MavenId mavenId,
                                      @NotNull final DependencyScope scope) {
    return addDependency(fromModules, mavenId, null, null, null, scope);
  }

  private Promise<Void> addDependency(@NotNull Collection<? extends Module> fromModules,
                                      @NotNull final MavenId mavenId,
                                      @Nullable String minVersion,
                                      @Nullable String maxVersion,
                                      @Nullable String preferredVersion, @NotNull final DependencyScope scope) {
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
          version = selectVersion(mavenId, minVersion, maxVersion, preferredVersion);
          scopeToSet = mavenScope;
        }
      }

      models.add(Trinity.create(model, new MavenId(mavenId.getGroupId(), mavenId.getArtifactId(), version), scopeToSet));
      files.add(DomUtil.getFile(model));
      projectToUpdate.add(fromProject);
    }

    WriteCommandAction.writeCommandAction(myProject, PsiUtilCore.toPsiFileArray(files)).withName(MavenDomBundle.message("fix.add.dependency")).run(() -> {
                                                                                                 PsiDocumentManager pdm = PsiDocumentManager.getInstance(myProject);
      for (Trinity<MavenDomProjectModel, MavenId, String> trinity : models) {
        final MavenDomProjectModel model = trinity.first;
        MavenDomDependency dependency = MavenDomUtil.createDomDependency(model, null, trinity.second);
        String ms = trinity.third;
        if (ms != null) {
          dependency.getScope().setStringValue(ms);
        }
        Document document = pdm.getDocument(DomUtil.getFile(model));
        if (document != null) {
          pdm.doPostponedOperationsAndUnblockDocument(document);
          FileDocumentManager.getInstance().saveDocument(document);
        }
      }
    });
    return myProjectsManager.forceUpdateProjects(projectToUpdate);
  }

  @Nullable
  @Override
  public Promise<Void> addExternalLibraryDependency(@NotNull Collection<? extends Module> modules,
                                                    @NotNull ExternalLibraryDescriptor descriptor,
                                                    @NotNull DependencyScope scope) {
    for (Module module : modules) {
      if (!myProjectsManager.isMavenizedModule(module)) {
        return null;
      }
    }

    MavenId mavenId = new MavenId(descriptor.getLibraryGroupId(), descriptor.getLibraryArtifactId(), null);
    return addDependency(modules, mavenId, descriptor.getMinVersion(), descriptor.getMaxVersion(), descriptor.getPreferredVersion(), scope);
  }

  @NotNull
  private String selectVersion(@NotNull MavenId mavenId,
                               @Nullable String minVersion,
                               @Nullable String maxVersion,
                               @Nullable String preferredVersion) {
    Set<String> versions = (mavenId.getGroupId() == null || mavenId.getArtifactId() == null)
                           ? Collections.emptySet()
                           : DependencySearchService.getInstance(myProject).getVersions(mavenId.getGroupId(), mavenId.getArtifactId());
    if (preferredVersion != null && versions.contains(preferredVersion)) {
      return preferredVersion;
    }
    List<String> suitableVersions = new ArrayList<>();
    for (String version : versions) {
      if ((minVersion == null || VersionComparatorUtil.compare(minVersion, version) <= 0)
          && (maxVersion == null || VersionComparatorUtil.compare(version, maxVersion) <= 0)) {
        suitableVersions.add(version);
      }
    }
    if (suitableVersions.isEmpty()) {
      return mavenId.getVersion() == null ? "RELEASE" : mavenId.getVersion();
    }
    return Collections.max(suitableVersions, VersionComparatorUtil.COMPARATOR);
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

    WriteCommandAction.writeCommandAction(myProject, DomUtil.getFile(model)).withName(MavenDomBundle.message("fix.add.dependency")).run(() -> {
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
      Document document = documentManager.getDocument(DomUtil.getFile(model));
      if (document != null) {
        documentManager.commitDocument(document);
      }
      XmlTag tag = getCompilerPlugin(model).getConfiguration().ensureTagExists();
      String option = JpsJavaSdkType.complianceOption(level.toJavaVersion());
      setChildTagValue(tag, "source", option);
      setChildTagValue(tag, "target", option);
      if (level.isPreview()) {
        setChildTagValue(tag, "compilerArgs","--enable-preview");
      }
      if (document != null) {
        FileDocumentManager.getInstance().saveDocument(document);
      }
    });
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
  public static MavenDomPlugin getCompilerPlugin(MavenDomProjectModel model) {
    MavenDomPlugin plugin = findCompilerPlugin(model);
    if (plugin != null) return plugin;
    plugin = model.getBuild().getPlugins().addPlugin();
    plugin.getGroupId().setValue("org.apache.maven.plugins");
    plugin.getArtifactId().setValue("maven-compiler-plugin");
    return plugin;
  }

  @Nullable
  public static MavenDomPlugin findCompilerPlugin(MavenDomProjectModel model) {
    MavenDomPlugins plugins = model.getBuild().getPlugins();
    for (MavenDomPlugin plugin : plugins.getPlugins()) {
      if ("org.apache.maven.plugins".equals(plugin.getGroupId().getStringValue()) &&
          "maven-compiler-plugin".equals(plugin.getArtifactId().getStringValue())) {
        return plugin;
      }
    }
    return null;
  }

  private static String getMavenScope(@NotNull DependencyScope scope) {
    return switch (scope) {
      case RUNTIME -> MavenConstants.SCOPE_RUNTIME;
      case COMPILE -> MavenConstants.SCOPE_COMPILE;
      case TEST -> MavenConstants.SCOPE_TEST;
      case PROVIDED -> MavenConstants.SCOPE_PROVIDED;
    };
  }
}
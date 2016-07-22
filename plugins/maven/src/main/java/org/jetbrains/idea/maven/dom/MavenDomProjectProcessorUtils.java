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
package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.impl.GenericDomValueReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.*;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class MavenDomProjectProcessorUtils {
  private MavenDomProjectProcessorUtils() {
  }

  @NotNull
  public static Set<MavenDomProjectModel> getChildrenProjects(@NotNull final MavenDomProjectModel model) {
    Set<MavenDomProjectModel> models = new HashSet<>();

    collectChildrenProjects(model, models);

    return models;
  }

  private static void collectChildrenProjects(@NotNull final MavenDomProjectModel model, @NotNull Set<MavenDomProjectModel> models) {
    MavenProject mavenProject = MavenDomUtil.findProject(model);
    if (mavenProject != null) {
      final Project project = model.getManager().getProject();
      for (MavenProject inheritor : MavenProjectsManager.getInstance(project).findInheritors(mavenProject)) {
        MavenDomProjectModel inheritorProjectModel = MavenDomUtil.getMavenDomProjectModel(project, inheritor.getFile());
        if (inheritorProjectModel != null && !models.contains(inheritorProjectModel)) {
          models.add(inheritorProjectModel);
          collectChildrenProjects(inheritorProjectModel, models);
        }
      }
    }
  }

  @NotNull
  public static Set<MavenDomProjectModel> collectParentProjects(@NotNull final MavenDomProjectModel projectDom) {
    final Set<MavenDomProjectModel> parents = new HashSet<>();

    Processor<MavenDomProjectModel> collectProcessor = model -> {
      parents.add(model);
      return false;
    };
    processParentProjects(projectDom, collectProcessor);

    return parents;
  }

  public static void processParentProjects(@NotNull final MavenDomProjectModel projectDom,
                                           @NotNull final Processor<MavenDomProjectModel> processor) {
    Set<MavenDomProjectModel> processed = new HashSet<>();
    Project project = projectDom.getManager().getProject();
    MavenDomProjectModel parent = findParent(projectDom, project);
    while (parent != null) {
      if (processed.contains(parent)) break;
      processed.add(parent);
      if (processor.process(parent)) break;

      parent = findParent(parent, project);
    }
  }

  @Nullable
  public static MavenDomProjectModel findParent(@NotNull MavenDomProjectModel model, Project project) {
    return findParent(model.getMavenParent(), project);
  }

  @Nullable
  public static MavenDomProjectModel findParent(@NotNull MavenDomParent mavenDomParent, Project project) {
    if (!DomUtil.hasXml(mavenDomParent)) return null;

    MavenId id = new MavenId(mavenDomParent.getGroupId().getStringValue(), mavenDomParent.getArtifactId().getStringValue(),
                             mavenDomParent.getVersion().getStringValue());
    MavenProject mavenProject = MavenProjectsManager.getInstance(project).findProject(id);

    return mavenProject != null ? MavenDomUtil.getMavenDomProjectModel(project, mavenProject.getFile()) : null;
  }

  @Nullable
  public static XmlTag searchProperty(@NotNull final String propertyName,
                                      @NotNull MavenDomProjectModel projectDom,
                                      @NotNull final Project project) {
    SearchProcessor<XmlTag, MavenDomProperties> searchProcessor = new SearchProcessor<XmlTag, MavenDomProperties>() {
      @Override
      protected XmlTag find(MavenDomProperties element) {
        return findProperty(element, propertyName);
      }
    };

    processProperties(projectDom, searchProcessor, project);
    return searchProcessor.myResult;
  }

  @Nullable
  public static XmlTag findProperty(@NotNull MavenDomProperties mavenDomProperties, @NotNull String propertyName) {
    XmlTag propertiesTag = mavenDomProperties.getXmlTag();
    if (propertiesTag == null) return null;

    for (XmlTag each : propertiesTag.getSubTags()) {
      if (each.getName().equals(propertyName)) {
        return each;
      }
    }

    return null;
  }

  public static Set<XmlTag> collectProperties(@NotNull MavenDomProjectModel projectDom, @NotNull final Project project) {
    final Set<XmlTag> properties = new HashSet<>();

    Processor<MavenDomProperties> collectProcessor = mavenDomProperties -> {
      XmlTag propertiesTag = mavenDomProperties.getXmlTag();
      if (propertiesTag != null) {
        ContainerUtil.addAll(properties, propertiesTag.getSubTags());
      }
      return false;
    };

    processProperties(projectDom, collectProcessor, project);

    return properties;
  }


  @NotNull
  public static Set<MavenDomDependency> searchDependencyUsages(@NotNull final MavenDomDependency dependency) {
    final MavenDomProjectModel model = dependency.getParentOfType(MavenDomProjectModel.class, false);
    if (model != null) {
      DependencyConflictId dependencyId = DependencyConflictId.create(dependency);
      if (dependencyId != null) {
        return searchDependencyUsages(model, dependencyId, Collections.singleton(dependency));
      }
    }
    return Collections.emptySet();
  }

  @NotNull
  public static Set<MavenDomDependency> searchDependencyUsages(@NotNull final MavenDomProjectModel model,
                                                               @NotNull final DependencyConflictId dependencyId,
                                                               @NotNull final Set<MavenDomDependency> excludes) {
    Project project = model.getManager().getProject();
    final Set<MavenDomDependency> usages = new HashSet<>();
    Processor<MavenDomProjectModel> collectProcessor = mavenDomProjectModel -> {
      for (MavenDomDependency domDependency : mavenDomProjectModel.getDependencies().getDependencies()) {
        if (excludes.contains(domDependency)) continue;

        if (dependencyId.equals(DependencyConflictId.create(domDependency))) {
          usages.add(domDependency);
        }
      }
      return false;
    };

    processChildrenRecursively(model, collectProcessor, project, new HashSet<>(), true);

    return usages;
  }

  @NotNull
  public static Collection<MavenDomPlugin> searchManagedPluginUsages(@NotNull final MavenDomPlugin plugin) {
    String artifactId = plugin.getArtifactId().getStringValue();
    if (artifactId == null) return Collections.emptyList();

    String groupId = plugin.getGroupId().getStringValue();

    MavenDomProjectModel model = plugin.getParentOfType(MavenDomProjectModel.class, false);
    if (model == null) return Collections.emptyList();

    return searchManagedPluginUsages(model, groupId, artifactId);
  }

  @NotNull
  public static Collection<MavenDomPlugin> searchManagedPluginUsages(@NotNull final MavenDomProjectModel model,
                                                                     @Nullable final String groupId,
                                                                     @NotNull final String artifactId) {
    Project project = model.getManager().getProject();

    final Set<MavenDomPlugin> usages = new HashSet<>();

    Processor<MavenDomProjectModel> collectProcessor = mavenDomProjectModel -> {
      for (MavenDomPlugin domPlugin : mavenDomProjectModel.getBuild().getPlugins().getPlugins()) {
        if (MavenPluginDomUtil.isPlugin(domPlugin, groupId, artifactId)) {
          usages.add(domPlugin);
        }
      }
      return false;
    };

    processChildrenRecursively(model, collectProcessor, project, new HashSet<>(), true);

    return usages;
  }

  public static void processChildrenRecursively(@Nullable MavenDomProjectModel model,
                                                @NotNull Processor<MavenDomProjectModel> processor) {
    processChildrenRecursively(model, processor, true);
  }

  public static void processChildrenRecursively(@Nullable MavenDomProjectModel model,
                                                @NotNull Processor<MavenDomProjectModel> processor,
                                                boolean processCurrentModel) {
    if (model != null) {
      processChildrenRecursively(model, processor, model.getManager().getProject(), new HashSet<>(),
                                 processCurrentModel);
    }
  }

  public static void processChildrenRecursively(@Nullable MavenDomProjectModel model,
                                                @NotNull Processor<MavenDomProjectModel> processor,
                                                @NotNull Project project,
                                                @NotNull Set<MavenDomProjectModel> processedModels,
                                                boolean strict) {
    if (model != null && !processedModels.contains(model)) {
      processedModels.add(model);

      if (strict && processor.process(model)) return;

      MavenProject mavenProject = MavenDomUtil.findProject(model);
      if (mavenProject != null) {
        for (MavenProject childProject : MavenProjectsManager.getInstance(project).findInheritors(mavenProject)) {
          MavenDomProjectModel childProjectModel = MavenDomUtil.getMavenDomProjectModel(project, childProject.getFile());

          processChildrenRecursively(childProjectModel, processor, project, processedModels, true);
        }
      }
    }
  }

  @Nullable
  public static MavenDomDependency searchManagingDependency(@NotNull final MavenDomDependency dependency) {
    return searchManagingDependency(dependency, dependency.getManager().getProject());
  }

  @Nullable
  public static MavenDomDependency searchManagingDependency(@NotNull final MavenDomDependency dependency, @NotNull final Project project) {
    final DependencyConflictId depId = DependencyConflictId.create(dependency);
    if (depId == null) return null;

    final MavenDomProjectModel model = dependency.getParentOfType(MavenDomProjectModel.class, false);
    if (model == null) return null;

    final Ref<MavenDomDependency> res = new Ref<>();

    Processor<MavenDomDependency> processor = dependency1 -> {
      if (depId.equals(DependencyConflictId.create(dependency1))) {
        res.set(dependency1);
        return true;
      }

      return false;
    };

    processDependenciesInDependencyManagement(model, processor, project);

    return res.get();
  }

  @Nullable
  public static MavenDomPlugin searchManagingPlugin(@NotNull final MavenDomPlugin plugin) {
    final String artifactId = plugin.getArtifactId().getStringValue();
    final String groupId = plugin.getGroupId().getStringValue();
    if (artifactId == null) return null;

    final MavenDomProjectModel model = plugin.getParentOfType(MavenDomProjectModel.class, false);
    if (model == null) return null;

    SearchProcessor<MavenDomPlugin, MavenDomPlugins> processor = new SearchProcessor<MavenDomPlugin, MavenDomPlugins>() {
      @Override
      protected MavenDomPlugin find(MavenDomPlugins mavenDomPlugins) {
        if (!model.equals(mavenDomPlugins.getParentOfType(MavenDomProjectModel.class, true))) {
          for (MavenDomPlugin domPlugin : mavenDomPlugins.getPlugins()) {
            if (MavenPluginDomUtil.isPlugin(domPlugin, groupId, artifactId)) {
              return domPlugin;
            }
          }
        }

        return null;
      }
    };

    Function<MavenDomProjectModelBase, MavenDomPlugins> domProfileFunction =
      mavenDomProfile -> mavenDomProfile.getBuild().getPluginManagement().getPlugins();

    process(model, processor, model.getManager().getProject(), domProfileFunction, domProfileFunction);

    return processor.myResult;
  }


  public static boolean processDependenciesInDependencyManagement(@NotNull MavenDomProjectModel projectDom,
                                                                  @NotNull final Processor<MavenDomDependency> processor,
                                                                  @NotNull final Project project) {

    Processor<MavenDomDependencies> managedDependenciesListProcessor = dependencies -> {
      SmartList<MavenDomDependency> importDependencies = null;

      for (MavenDomDependency domDependency : dependencies.getDependencies()) {
        if ("import".equals(domDependency.getScope().getRawText())) {
          if (importDependencies == null) {
            importDependencies = new SmartList<>();
          }

          importDependencies.add(domDependency);
        }
        else {
          if (processor.process(domDependency)) return true;
        }
      }

      if (importDependencies != null) {
        for (MavenDomDependency domDependency : importDependencies) {
          GenericDomValue<String> version = domDependency.getVersion();
          if (version.getXmlElement() != null) {
            GenericDomValueReference reference = new GenericDomValueReference(version);
            PsiElement resolve = reference.resolve();

            if (resolve instanceof XmlFile) {
              MavenDomProjectModel dependModel = MavenDomUtil.getMavenDomModel((PsiFile)resolve, MavenDomProjectModel.class);
              if (dependModel != null) {
                for (MavenDomDependency dep : dependModel.getDependencyManagement().getDependencies().getDependencies()) {
                  if (processor.process(dep)) return true;
                }
              }
            }
          }
        }
      }

      return false;
    };

    Function<MavenDomProjectModelBase, MavenDomDependencies> domFunction =
      mavenDomProfile -> mavenDomProfile.getDependencyManagement().getDependencies();

    return process(projectDom, managedDependenciesListProcessor, project, domFunction, domFunction);
  }

  public static boolean processDependencies(@NotNull MavenDomProjectModel projectDom,
                                            @NotNull final Processor<MavenDomDependencies> processor) {

    Function<MavenDomProjectModelBase, MavenDomDependencies> domFunction = mavenDomProfile -> mavenDomProfile.getDependencies();

    return process(projectDom, processor, projectDom.getManager().getProject(), domFunction, domFunction);
  }

  public static boolean processProperties(@NotNull MavenDomProjectModel projectDom,
                                          @NotNull final Processor<MavenDomProperties> processor,
                                          @NotNull final Project project) {

    Function<MavenDomProjectModelBase, MavenDomProperties> domFunction = mavenDomProfile -> mavenDomProfile.getProperties();

    return process(projectDom, processor, project, domFunction, domFunction);
  }

  public static <T> boolean process(@NotNull MavenDomProjectModel projectDom,
                                    @NotNull final Processor<T> processor,
                                    @NotNull final Project project,
                                    @NotNull final Function<? super MavenDomProfile , T> domProfileFunction,
                                    @NotNull final Function<? super MavenDomProjectModel, T> projectDomFunction) {

    return process(projectDom, processor, project, domProfileFunction, projectDomFunction, new HashSet<>());
  }


  public static <T> boolean process(@NotNull MavenDomProjectModel projectDom,
                                    @NotNull final Processor<T> processor,
                                    @NotNull final Project project,
                                    @NotNull final Function<? super MavenDomProfile, T> domProfileFunction,
                                    @NotNull final Function<? super MavenDomProjectModel, T> projectDomFunction,
                                    final Set<MavenDomProjectModel> processed) {
    if (processed.contains(projectDom)) return true;
    processed.add(projectDom);

    MavenProject mavenProjectOrNull = MavenDomUtil.findProject(projectDom);

    if (processSettingsXml(mavenProjectOrNull, processor, project, domProfileFunction)) return true;
    if (processProject(projectDom, mavenProjectOrNull, processor, project, domProfileFunction, projectDomFunction)) return true;

    return processParentProjectFile(projectDom, processor, project, domProfileFunction, projectDomFunction, processed);
  }

  private static <T> boolean processParentProjectFile(MavenDomProjectModel projectDom,
                                                      final Processor<T> processor,
                                                      final Project project,
                                                      final Function<? super MavenDomProfile, T> domProfileFunction,
                                                      final Function<? super MavenDomProjectModel, T> projectDomFunction,
                                                      final Set<MavenDomProjectModel> processed) {
    Boolean aBoolean = new DomParentProjectFileProcessor<Boolean>(MavenProjectsManager.getInstance(project)) {
      protected Boolean doProcessParent(VirtualFile parentFile) {
        MavenDomProjectModel parentProjectDom = MavenDomUtil.getMavenDomProjectModel(project, parentFile);
        if (parentProjectDom == null) return false;

        return MavenDomProjectProcessorUtils
          .process(parentProjectDom, processor, project, domProfileFunction, projectDomFunction, processed);
      }
    }.process(projectDom);

    return aBoolean == null ? false : aBoolean.booleanValue();
  }


  private static <T> boolean processSettingsXml(@Nullable MavenProject mavenProject,
                                                @NotNull Processor<T> processor,
                                                @NotNull Project project,
                                                Function<? super MavenDomProfile, T> domProfileFunction) {
    MavenGeneralSettings settings = MavenProjectsManager.getInstance(project).getGeneralSettings();

    for (VirtualFile each : settings.getEffectiveSettingsFiles()) {
      MavenDomSettingsModel settingsDom = MavenDomUtil.getMavenDomModel(project, each, MavenDomSettingsModel.class);
      if (settingsDom == null) continue;

      if (processProfiles(settingsDom.getProfiles(), mavenProject, processor, domProfileFunction)) return true;
    }
    return false;
  }

  private static <T> boolean processProject(MavenDomProjectModel projectDom,
                                            MavenProject mavenProjectOrNull,
                                            Processor<T> processor,
                                            Project project,
                                            Function<? super MavenDomProfile, T> domProfileFunction,
                                            Function<? super MavenDomProjectModel, T> projectDomFunction) {

    if (processProfilesXml(MavenDomUtil.getVirtualFile(projectDom), mavenProjectOrNull, processor, project, domProfileFunction)) {
      return true;
    }

    if (processProfiles(projectDom.getProfiles(), mavenProjectOrNull, processor, domProfileFunction)) return true;

    T t = projectDomFunction.fun(projectDom);
    return t == null ? false : processor.process(t);
  }

  private static <T> boolean processProfilesXml(VirtualFile projectFile,
                                                MavenProject mavenProjectOrNull,
                                                Processor<T> processor,
                                                Project project,
                                                Function<? super MavenDomProfile, T> f) {
    VirtualFile profilesFile = MavenUtil.findProfilesXmlFile(projectFile);
    if (profilesFile == null) return false;

    MavenDomProfiles profiles = MavenDomUtil.getMavenDomProfilesModel(project, profilesFile);
    if (profiles == null) return false;

    return processProfiles(profiles, mavenProjectOrNull, processor, f);
  }

  private static <T> boolean processProfiles(MavenDomProfiles profilesDom,
                                             MavenProject mavenProjectOrNull,
                                             Processor<T> processor,
                                             Function<? super MavenDomProfile, T> f) {
    Collection<String> activeProfiles =
      mavenProjectOrNull == null ? null : mavenProjectOrNull.getActivatedProfilesIds().getEnabledProfiles();
    for (MavenDomProfile each : profilesDom.getProfiles()) {
      XmlTag idTag = each.getId().getXmlTag();
      if (idTag == null) continue;
      if (activeProfiles != null && !activeProfiles.contains(idTag.getValue().getTrimmedText())) continue;

      if (processProfile(each, processor, f)) return true;
    }
    return false;
  }

  private static <T> boolean processProfile(MavenDomProfile profileDom,
                                            Processor<T> processor,
                                            Function<? super MavenDomProfile, T> f) {
    T t = f.fun(profileDom);
    return t != null && processor.process(t);
  }

  public abstract static class DomParentProjectFileProcessor<T> extends MavenParentProjectFileProcessor<T> {
    private final MavenProjectsManager myManager;

    public DomParentProjectFileProcessor(MavenProjectsManager manager) {
      myManager = manager;
    }

    protected VirtualFile findManagedFile(@NotNull MavenId id) {
      MavenProject project = myManager.findProject(id);
      return project == null ? null : project.getFile();
    }

    @Nullable
    public T process(@NotNull MavenDomProjectModel projectDom) {
      MavenDomParent parent = projectDom.getMavenParent();
      MavenParentDesc parentDesc = null;
      if (DomUtil.hasXml(parent)) {
        String parentGroupId = parent.getGroupId().getStringValue();
        String parentArtifactId = parent.getArtifactId().getStringValue();
        String parentVersion = parent.getVersion().getStringValue();
        String parentRelativePath = parent.getRelativePath().getStringValue();
        if (StringUtil.isEmptyOrSpaces(parentRelativePath)) parentRelativePath = "../pom.xml";
        MavenId parentId = new MavenId(parentGroupId, parentArtifactId, parentVersion);
        parentDesc = new MavenParentDesc(parentId, parentRelativePath);
      }

      return process(myManager.getGeneralSettings(), MavenDomUtil.getVirtualFile(projectDom), parentDesc);
    }
  }

  public abstract static class SearchProcessor<R, T> implements Processor<T> {

    private R myResult;

    @Override
    public final boolean process(T t) {
      R res = find(t);
      if (res != null) {
        myResult = res;
        return true;
      }

      return false;
    }

    @Nullable
    protected abstract R find(T element);

    public R getResult() {
      return myResult;
    }
  }
}

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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.*;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class MavenDomProjectProcessorUtils {
  private MavenDomProjectProcessorUtils() {
  }

  @NotNull
  public static Set<MavenDomProjectModel> getChildrenProjects(@NotNull final MavenDomProjectModel model) {
    Set<MavenDomProjectModel> models = new HashSet<MavenDomProjectModel>();

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
    final Set<MavenDomProjectModel> parents = new HashSet<MavenDomProjectModel>();

    Processor<MavenDomProjectModel> collectProcessor = new Processor<MavenDomProjectModel>() {
      public boolean process(MavenDomProjectModel model) {
        parents.add(model);
        return false;
      }
    };
    processParentProjects(projectDom, collectProcessor);

    return parents;
  }

  public static void processParentProjects(@NotNull final MavenDomProjectModel projectDom,
                                           @NotNull final Processor<MavenDomProjectModel> processor) {
    Set<MavenDomProjectModel> processed = new HashSet<MavenDomProjectModel>();
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
    final XmlTag[] property = new XmlTag[]{null};

    Processor<MavenDomProperties> searchProcessor = new Processor<MavenDomProperties>() {
      public boolean process(MavenDomProperties mavenDomProperties) {
        XmlTag propertiesTag = mavenDomProperties.getXmlTag();
        if (propertiesTag != null) {
          for (XmlTag each : propertiesTag.getSubTags()) {
            if (each.getName().equals(propertyName)) {
              property[0] = each;
              return true;
            }
          }
        }
        return false;
      }
    };

    processProperties(projectDom, searchProcessor, project);

    return property[0];
  }

  @Nullable
  public static Set<XmlTag> collectProperties(@NotNull MavenDomProjectModel projectDom, @NotNull final Project project) {
    final Set<XmlTag> properties = new HashSet<XmlTag>();

    Processor<MavenDomProperties> collectProcessor = new Processor<MavenDomProperties>() {
      public boolean process(MavenDomProperties mavenDomProperties) {
        XmlTag propertiesTag = mavenDomProperties.getXmlTag();
        if (propertiesTag != null) {
          ContainerUtil.addAll(properties, propertiesTag.getSubTags());
        }
        return false;
      }
    };

    processProperties(projectDom, collectProcessor, project);

    return properties;
  }


  @NotNull
  public static Set<MavenDomDependency> searchDependencyUsages(@NotNull final MavenDomDependency dependency,
                                                               @NotNull final Project project) {
    final MavenDomProjectModel model = dependency.getParentOfType(MavenDomProjectModel.class, false);
    if (model != null) {
      final String artifactId = dependency.getArtifactId().getStringValue();
      final String groupId = dependency.getGroupId().getStringValue();
      if (artifactId != null && groupId != null) {
        return searchDependencyUsages(model, groupId, artifactId, Collections.singleton(dependency));
      }
    }
    return Collections.emptySet();
  }

  @NotNull
  public static Set<MavenDomDependency> searchDependencyUsages(@NotNull final MavenDomProjectModel model,
                                                               @NotNull final String groupId,
                                                               @NotNull final String artifactId) {
    return searchDependencyUsages(model, groupId, artifactId, Collections.<MavenDomDependency>emptySet());
  }

  @NotNull
  public static Set<MavenDomDependency> searchDependencyUsages(@NotNull final MavenDomProjectModel model,
                                                               @Nullable final String groupId,
                                                               @Nullable final String artifactId,
                                                               @NotNull final Set<MavenDomDependency> excludes) {

    if (groupId == null || artifactId == null) return Collections.emptySet();

    Project project = model.getManager().getProject();
    final Set<MavenDomDependency> usages = new HashSet<MavenDomDependency>();
    Processor<MavenDomProjectModel> collectProcessor = new Processor<MavenDomProjectModel>() {
      public boolean process(MavenDomProjectModel mavenDomProjectModel) {
        if (!model.equals(mavenDomProjectModel)) {
          for (MavenDomDependency domDependency : mavenDomProjectModel.getDependencies().getDependencies()) {
            if (excludes.contains(domDependency)) continue;
            if (artifactId.equals(domDependency.getArtifactId().getStringValue()) &&
                groupId.equals(domDependency.getGroupId().getStringValue())) {
              usages.add(domDependency);
            }
          }
        }
        return false;
      }
    };

    processChildrenRecursively(model, collectProcessor, project, new HashSet<MavenDomProjectModel>(), true);

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
      processChildrenRecursively(model, processor, model.getManager().getProject(), new HashSet<MavenDomProjectModel>(),
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

  @NotNull
  public static Set<MavenDomDependency> collectManagingDependencies(@NotNull MavenDomProjectModel model) {
    final Set<MavenDomDependency> dependencies = new HashSet<MavenDomDependency>();

    Processor<MavenDomDependencies> collectProcessor = new Processor<MavenDomDependencies>() {
      public boolean process(MavenDomDependencies mavenDomDependencies) {
        dependencies.addAll(mavenDomDependencies.getDependencies());
        return false;
      }
    };

    processDependenciesInDependencyManagement(model, collectProcessor, model.getManager().getProject());

    return dependencies;
  }

  @Nullable
  public static MavenDomDependency searchManagingDependency(@NotNull final MavenDomDependency dependency) {
    return searchManagingDependency(dependency, dependency.getManager().getProject());
  }

  @Nullable
  public static MavenDomDependency searchManagingDependency(@NotNull final MavenDomDependency dependency, @NotNull final Project project) {
    final MavenDomDependency[] parent = new MavenDomDependency[]{null};

    final String artifactId = dependency.getArtifactId().getStringValue();
    final String groupId = dependency.getGroupId().getStringValue();
    if (artifactId != null && groupId != null) {
      final MavenDomProjectModel model = dependency.getParentOfType(MavenDomProjectModel.class, false);
      if (model != null) {
        Processor<MavenDomDependencies> processor = new Processor<MavenDomDependencies>() {
          public boolean process(MavenDomDependencies mavenDomDependencies) {
            if (!model.equals(mavenDomDependencies.getParentOfType(MavenDomProjectModel.class, true))) {
              for (MavenDomDependency domDependency : mavenDomDependencies.getDependencies()) {
                if (domDependency.equals(dependency)) continue;
                if (artifactId.equals(domDependency.getArtifactId().getStringValue()) &&
                    groupId.equals(domDependency.getGroupId().getStringValue())) {
                  parent[0] = domDependency;
                  return true;
                }
              }
            }
            return false;
          }
        };
        processDependenciesInDependencyManagement(model, processor, project);
      }
    }

    return parent[0];
  }


  public static boolean processDependenciesInDependencyManagement(@NotNull MavenDomProjectModel projectDom,
                                                                  @NotNull final Processor<MavenDomDependencies> processor,
                                                                  @NotNull final Project project) {

    Function<MavenDomProfile, MavenDomDependencies> domProfileFunction = new Function<MavenDomProfile, MavenDomDependencies>() {
      public MavenDomDependencies fun(MavenDomProfile mavenDomProfile) {
        return mavenDomProfile.getDependencyManagement().getDependencies();
      }
    };
    Function<MavenDomProjectModel, MavenDomDependencies> projectDomFunction = new Function<MavenDomProjectModel, MavenDomDependencies>() {
      public MavenDomDependencies fun(MavenDomProjectModel mavenDomProjectModel) {
        return mavenDomProjectModel.getDependencyManagement().getDependencies();
      }
    };

    return process(projectDom, processor, project, domProfileFunction, projectDomFunction);
  }

  public static boolean processProperties(@NotNull MavenDomProjectModel projectDom,
                                          @NotNull final Processor<MavenDomProperties> processor,
                                          @NotNull final Project project) {

    Function<MavenDomProfile, MavenDomProperties> domProfileFunction = new Function<MavenDomProfile, MavenDomProperties>() {
      public MavenDomProperties fun(MavenDomProfile mavenDomProfile) {
        return mavenDomProfile.getProperties();
      }
    };
    Function<MavenDomProjectModel, MavenDomProperties> projectDomFunction = new Function<MavenDomProjectModel, MavenDomProperties>() {
      public MavenDomProperties fun(MavenDomProjectModel mavenDomProjectModel) {
        return mavenDomProjectModel.getProperties();
      }
    };

    return process(projectDom, processor, project, domProfileFunction, projectDomFunction);
  }

  public static <T> boolean process(@NotNull MavenDomProjectModel projectDom,
                                    @NotNull final Processor<T> processor,
                                    @NotNull final Project project,
                                    @NotNull final Function<MavenDomProfile, T> domProfileFunction,
                                    @NotNull final Function<MavenDomProjectModel, T> projectDomFunction) {

    return process(projectDom, processor, project, domProfileFunction, projectDomFunction, new HashSet<MavenDomProjectModel>());
  }


  public static <T> boolean process(@NotNull MavenDomProjectModel projectDom,
                                    @NotNull final Processor<T> processor,
                                    @NotNull final Project project,
                                    @NotNull final Function<MavenDomProfile, T> domProfileFunction,
                                    @NotNull final Function<MavenDomProjectModel, T> projectDomFunction,
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
                                                      final Function<MavenDomProfile, T> domProfileFunction,
                                                      final Function<MavenDomProjectModel, T> projectDomFunction,
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
                                                Function<MavenDomProfile, T> domProfileFunction) {
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
                                            Function<MavenDomProfile, T> domProfileFunction,
                                            Function<MavenDomProjectModel, T> projectDomFunction) {

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
                                                Function<MavenDomProfile, T> f) {
    VirtualFile profilesFile = MavenUtil.findProfilesXmlFile(projectFile);
    if (profilesFile == null) return false;

    MavenDomProfiles profiles = MavenDomUtil.getMavenDomProfilesModel(project, profilesFile);
    if (profiles == null) return false;

    return processProfiles(profiles, mavenProjectOrNull, processor, f);
  }

  private static <T> boolean processProfiles(MavenDomProfiles profilesDom,
                                             MavenProject mavenProjectOrNull,
                                             Processor<T> processor,
                                             Function<MavenDomProfile, T> f) {
    Collection<String> activePropfiles = mavenProjectOrNull == null ? null : mavenProjectOrNull.getActiveProfilesIds();
    for (MavenDomProfile each : profilesDom.getProfiles()) {
      XmlTag idTag = each.getId().getXmlTag();
      if (idTag == null) continue;
      if (activePropfiles != null && !activePropfiles.contains(idTag.getValue().getText())) continue;

      T t = f.fun(each);
      if (t != null && processor.process(t)) return true;
    }
    return false;
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

}

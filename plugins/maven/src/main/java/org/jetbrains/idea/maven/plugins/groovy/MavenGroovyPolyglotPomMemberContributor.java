// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.plugins.groovy;

import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.util.dynamicMembers.DynamicMemberUtils;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

final class MavenGroovyPolyglotPomMemberContributor extends NonCodeMembersContributor {
  private static final Supplier<Collection<Contributor>> contributors = new SynchronizedClearableLazy<>(() -> {
    List<Contributor> list = new ArrayList<>();
    list.add(new Contributor("/maven/dsl/groovy/pom.groovy", ""));
    list.add(new Contributor("/maven/dsl/groovy/model.groovy", "project"));
    list.add(new Contributor("/maven/dsl/groovy/modelBase.groovy",
                             "project", "project->profiles->profile"));
    list.add(new Contributor("/maven/dsl/groovy/parent.groovy", "project->parent"));
    list.add(new Contributor("/maven/dsl/groovy/organization.groovy", "project->organization"));
    list.add(new Contributor("/maven/dsl/groovy/licenses.groovy", "project->licenses"));
    list.add(new Contributor("/maven/dsl/groovy/license.groovy", "project->licenses->license"));

    list.add(new Contributor("/maven/dsl/groovy/developers.groovy", "project->developers"));
    list.add(new Contributor("/maven/dsl/groovy/developer.groovy", "project->developers->developer"));
    list.add(new Contributor("/maven/dsl/groovy/contributor.groovy",
                             "project->developers->developer", "project->contributors->contributor"));
    list.add(new Contributor("/maven/dsl/groovy/contributors.groovy", "project->contributors"));

    list.add(new Contributor("/maven/dsl/groovy/modules.groovy", "project->modules"));

    list.add(new Contributor("/maven/dsl/groovy/dependencyManagement.groovy",
                             "project->dependencyManagement",
                             "project->profiles->profile->dependencyManagement"));
    list.add(new Contributor("/maven/dsl/groovy/distributionManagement.groovy",
                             "project->distributionManagement",
                             "project->profiles->profile->distributionManagement"));
    list.add(new Contributor("/maven/dsl/groovy/site.groovy",
                             "project->distributionManagement->site",
                             "project->profiles->profile->distributionManagement->site"));
    list.add(new Contributor("/maven/dsl/groovy/relocation.groovy",
                             "project->distributionManagement->relocation",
                             "project->profiles->profile->distributionManagement->relocation"));

    list.add(new Contributor("/maven/dsl/groovy/deploymentRepository.groovy",
                             "project->distributionManagement->repository",
                             "project->profiles->profile->distributionManagement->repository",
                             "project->distributionManagement->snapshotRepository",
                             "project->profiles->profile->distributionManagement->snapshotRepository"));


    list.add(new Contributor("/maven/dsl/groovy/dependencies.groovy",
                             "project->dependencies",
                             "project->dependencyManagement->dependencies",
                             "project->profiles->profile->dependencies",
                             "project->profiles->profile->dependencyManagement->dependencies",
                             "project->build->pluginManagement->plugins->plugin->dependencies",
                             "project->build->plugins->plugin->dependencies",
                             "project->profiles->profile->build->pluginManagement->plugins->plugin->dependencies",
                             "project->profiles->profile->build->plugins->plugin->dependencies"));
    list.add(new Contributor("/maven/dsl/groovy/dependency.groovy",
                             "project->dependencies->dependency",
                             "project->dependencyManagement->dependencies->dependency",
                             "project->profiles->profile->dependencies->dependency",
                             "project->profiles->profile->dependencyManagement->dependencies->dependency",
                             "project->build->plugins->plugin->dependencies->dependency",
                             "project->build->pluginManagement->plugins->plugin->dependencies->dependency",
                             "project->profiles->profile->build->plugins->plugin->dependencies->dependency",
                             "project->profiles->profile->build->pluginManagement->plugins->plugin->dependencies->dependency"));

    list.add(new Contributor("/maven/dsl/groovy/exclusions.groovy",
                             "project->dependencies->dependency->exclusions",
                             "project->dependencyManagement->dependencies->dependency->exclusions",
                             "project->profiles->profile->dependencies->dependency->exclusions",
                             "project->profiles->profile->dependencyManagement->dependencies->dependency->exclusions",
                             "project->build->plugins->plugin->dependencies->dependency->exclusions",
                             "project->build->pluginManagement->plugins->plugin->dependencies->dependency->exclusions",
                             "project->profiles->profile->build->plugins->plugin->dependencies->dependency->exclusions",
                             "project->profiles->profile->build->pluginManagement->plugins->plugin->dependencies->dependency->exclusions"));
    list.add(new Contributor("/maven/dsl/groovy/exclusion.groovy",
                             "project->dependencies->dependency->exclusions->exclusion",
                             "project->dependencyManagement->dependencies->dependency->exclusions->exclusion",
                             "project->profiles->profile->dependencies->dependency->exclusions->exclusion",
                             "project->profiles->profile->dependencyManagement->dependencies->dependency->exclusions->exclusion",
                             "project->build->plugins->plugin->dependencies->dependency->exclusions->exclusion",
                             "project->build->pluginManagement->plugins->plugin->dependencies->dependency->exclusions->exclusion",
                             "project->profiles->profile->build->plugins->plugin->dependencies->dependency->exclusions->exclusion",
                             "project->profiles->profile->build->pluginManagement->plugins->plugin->dependencies->dependency->exclusions->exclusion"));

    list.add(new Contributor("/maven/dsl/groovy/repositories.groovy",
                             "project->repositories",
                             "project->profiles->profile->repositories"));
    list.add(new Contributor("/maven/dsl/groovy/pluginRepositories.groovy",
                             "project->pluginRepositories",
                             "project->profiles->profile->pluginRepositories"));
    list.add(new Contributor("/maven/dsl/groovy/repository.groovy",
                             "project->repositories->repository",
                             "project->distributionManagement->repository",
                             "project->distributionManagement->snapshotRepository",
                             "project->pluginRepositories->pluginRepository",
                             "project->profiles->profile->repositories->repository",
                             "project->profiles->profile->pluginRepositories->pluginRepository",
                             "project->profiles->profile->distributionManagement->repository",
                             "project->profiles->profile->distributionManagement->snapshotRepository"));
    list.add(new Contributor("/maven/dsl/groovy/repositoryPolicy.groovy",
                             "project->repositories->repository->releases",
                             "project->repositories->repository->snapshots",
                             "project->distributionManagement->repository->releases",
                             "project->distributionManagement->repository->snapshots",
                             "project->distributionManagement->snapshotRepository->releases",
                             "project->distributionManagement->snapshotRepository->snapshots",
                             "project->pluginRepositories->pluginRepository->releases",
                             "project->pluginRepositories->pluginRepository->snapshots",
                             "project->profiles->profile->repositories->repository->releases",
                             "project->profiles->profile->repositories->repository->snapshots",
                             "project->profiles->profile->distributionManagement->repository->releases",
                             "project->profiles->profile->distributionManagement->repository->snapshots",
                             "project->profiles->profile->distributionManagement->snapshotRepository->releases",
                             "project->profiles->profile->distributionManagement->snapshotRepository->snapshots",
                             "project->profiles->profile->pluginRepositories->pluginRepository->releases",
                             "project->profiles->profile->pluginRepositories->pluginRepository->snapshots"));

    list.add(new Contributor("/maven/dsl/groovy/build.groovy",
                             "project->build",
                             "project->profiles->profile->build"));
    list.add(new Contributor("/maven/dsl/groovy/extensions.groovy",
                             "project->build->extensions",
                             "project->profiles->profile->build->extensions"));
    list.add(new Contributor("/maven/dsl/groovy/extension.groovy",
                             "project->build->extensions->extension",
                             "project->profiles->profile->build->extensions->extension"));
    list.add(new Contributor("/maven/dsl/groovy/resources.groovy",
                             "project->build->resources",
                             "project->profiles->profile->build->resources"));
    list.add(new Contributor("/maven/dsl/groovy/testResources.groovy",
                             "project->build->testResources",
                             "project->profiles->profile->build->testResources"));
    list.add(new Contributor("/maven/dsl/groovy/resource.groovy",
                             "project->build->resources->resource",
                             "project->build->testResources->testResource",
                             "project->profiles->profile->build->resources->resource",
                             "project->profiles->profile->build->testResources->testResource"));

    list.add(new Contributor("/maven/dsl/groovy/pluginConfiguration.groovy",
                             "project->build",
                             "project->profiles->profile->build"));
    list.add(new Contributor("/maven/dsl/groovy/pluginContainer.groovy",
                             "project->build",
                             "project->build->pluginManagement",
                             "project->profiles->profile->build",
                             "project->profiles->profile->build->pluginManagement"));
    list.add(new Contributor("/maven/dsl/groovy/plugins.groovy",
                             "project->build->plugins",
                             "project->build->pluginManagement->plugins",
                             "project->profiles->profile->build->plugins",
                             "project->profiles->profile->build->pluginManagement->plugins",
                             "project->reporting->plugins",
                             "project->profiles->profile->reporting->plugins"));
    list.add(new Contributor("/maven/dsl/groovy/plugin.groovy",
                             "project->build->plugins->plugin",
                             "project->build->pluginManagement->plugins->plugin",
                             "project->profiles->profile->build->plugins->plugin",
                             "project->profiles->profile->build->pluginManagement->plugins->plugin"));
    list.add(new Contributor("/maven/dsl/groovy/configurationContainer.groovy",
                             "project->build->plugins->plugin",
                             "project->build->plugins->plugin->executions->execution",
                             "project->build->pluginManagement->plugins->plugin",
                             "project->build->pluginManagement->plugins->plugin->executions->execution",
                             "project->reporting->plugins->plugin",
                             "project->reporting->plugins->plugin->reportSets->reportSet",

                             "project->profiles->profile->build->plugins->plugin",
                             "project->profiles->profile->build->plugins->plugin->executions->execution",
                             "project->profiles->profile->build->pluginManagement->plugins->plugin",
                             "project->profiles->profile->build->pluginManagement->plugins->plugin->executions->execution",
                             "project->profiles->profile->reporting->plugins->plugin",
                             "project->profiles->profile->reporting->plugins->plugin->reportSets->reportSet"));

    list.add(new Contributor("/maven/dsl/groovy/executions.groovy",
                             "project->build->plugins->plugin->executions",
                             "project->build->pluginManagement->plugins->plugin->executions",
                             "project->profiles->profile->build->plugins->plugin->executions",
                             "project->profiles->profile->build->pluginManagement->plugins->plugin->executions"));
    list.add(new Contributor("/maven/dsl/groovy/pluginExecution.groovy",
                             "project->build->plugins->plugin->executions->execution",
                             "project->build->pluginManagement->plugins->plugin->executions->execution",
                             "project->profiles->profile->build->plugins->plugin->executions->execution",
                             "project->profiles->profile->build->pluginManagement->plugins->plugin->executions->execution"));

    list.add(new Contributor("/maven/dsl/groovy/reporting.groovy", "project->reporting", "project->profiles->profile->reporting"));
    list.add(new Contributor("/maven/dsl/groovy/reportPlugin.groovy",
                             "project->reporting->plugins->plugin",
                             "project->profiles->profile->reporting->plugins->plugin"));
    list.add(new Contributor("/maven/dsl/groovy/reportSets.groovy",
                             "project->reporting->plugins->plugin->reportSets",
                             "project->profiles->profile->reporting->plugins->plugin->reportSets"));
    list.add(new Contributor("/maven/dsl/groovy/reportSet.groovy",
                             "project->reporting->plugins->plugin->reportSets->reportSet",
                             "project->profiles->profile->reporting->plugins->plugin->reportSets->reportSet"));

    list.add(new Contributor("/maven/dsl/groovy/profiles.groovy", "project->profiles"));
    list.add(new Contributor("/maven/dsl/groovy/profile.groovy", "project->profiles->profile"));
    list.add(new Contributor("/maven/dsl/groovy/activation.groovy", "project->profiles->profile->activation"));
    list.add(new Contributor("/maven/dsl/groovy/os.groovy", "project->profiles->profile->activation->os"));
    list.add(new Contributor("/maven/dsl/groovy/property.groovy",
                             "project->profiles->profile->activation->property"));
    list.add(new Contributor("/maven/dsl/groovy/activationFile.groovy", "project->profiles->profile->activation->file"));

    return list;
  });

  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     @Nullable PsiClass aClass,
                                     @NotNull PsiScopeProcessor processor,
                                     @NotNull PsiElement place,
                                     @NotNull ResolveState state) {
    if (!(aClass instanceof GroovyScriptClass)) {
      return;
    }

    PsiFile file = aClass.getContainingFile();
    if (!"pom.groovy".equals(file.getName())) return;

    List<String> methodCallInfo = MavenGroovyPomUtil.getGroovyMethodCalls(place);

    MultiMap<String, String> multiMap = MultiMap.createLinked();
    MultiMap<String, String> leafMap = MultiMap.createLinked();

    String key = StringUtil.join(methodCallInfo, "->");
    for (Contributor contributor : contributors.get()) {
      contributor.populate(place.getProject(), multiMap, leafMap);
    }

    for (String classSource : multiMap.get(key)) {
      DynamicMemberUtils.process(processor, false, place, classSource);
    }

    for (String classSource : leafMap.get(key)) {
      if (!(place.getParent() instanceof GrClosableBlock)) {
        DynamicMemberUtils.process(processor, false, place, classSource);
      }
    }

    if (!methodCallInfo.isEmpty() &&
        StringUtil.endsWithIgnoreCase(ContainerUtil.getLastItem(methodCallInfo), CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED)) {
      key = StringUtil.join(methodCallInfo.subList(0, methodCallInfo.size() - 1), "->");
      for (String classSource : multiMap.get(key)) {
        DynamicMemberUtils.process(processor, false, place, classSource);
      }
    }
  }

  private static final class Contributor {
    private final String myClassSourcePath;
    private final String[] myPaths;
    private final Supplier<String> myClassSourceValue;

    Contributor(@NotNull String classSourcePath, String... paths) {
      myClassSourcePath = classSourcePath;
      myPaths = paths;
      myClassSourceValue = new SynchronizedClearableLazy<>(() -> {
        try (InputStream stream = MavenGroovyPolyglotPomMemberContributor.class.getResourceAsStream(myClassSourcePath)) {
          if (stream != null) {
            return StreamUtil.readText(new InputStreamReader(stream, StandardCharsets.UTF_8));
          }
        }
        catch (Exception ignored) { }
        return null;
      });
    }

    public void populate(@NotNull Project project, @NotNull MultiMap<String, String> map, @NotNull MultiMap<String, String> leafMap) {
      String myClassSource = myClassSourceValue.get();
      if (myClassSource == null) return;

      for (String path : myPaths) {
        map.putValue(path, myClassSource);
      }

      final PsiMethod[] psiMethods = DynamicMemberUtils.getMembers(project, myClassSource).getMethods();
      for (String path : myPaths) {
        for (PsiMethod psiMethod : psiMethods) {
          leafMap.putValue(path.isEmpty() ? psiMethod.getName() : path + "->" + psiMethod.getName(), myClassSource);
        }
      }
    }
  }
}

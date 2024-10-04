// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.converters;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.ResolvingConverter;
import com.intellij.util.xml.impl.GenericDomValueReference;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.DependencyConflictId;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils;
import org.jetbrains.idea.maven.dom.model.*;
import org.jetbrains.idea.maven.indices.MavenIndexUtils;
import org.jetbrains.idea.maven.indices.MavenIndicesManager;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.reposearch.DependencySearchService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public abstract class MavenArtifactCoordinatesConverter extends ResolvingConverter<String> implements MavenDomSoftAwareConverter {
  @Override
  public String fromString(@Nullable @NonNls String s, @NotNull ConvertContext context) {
    if (s == null) return null;

    MavenId id = MavenArtifactCoordinatesHelper.getId(context);
    Project contextProject = context.getProject();
    MavenIndicesManager manager = MavenIndicesManager.getInstance(contextProject);

    ConverterStrategy strategy = selectStrategy(context);
    boolean isValid = strategy.isValid(id, manager, context);
    if (!isValid) {
      MavenRepositoryInfo repository = MavenIndexUtils.getLocalRepository(contextProject);
      if (repository == null) return null;
      Path artifactPath = MavenUtil.getArtifactPath(Path.of(repository.getUrl()), id, "pom", null);
      if (artifactPath != null && Files.exists(artifactPath)) {
        MavenIndicesManager.getInstance(contextProject).scheduleArtifactIndexing(id, artifactPath, repository.getUrl());
        return s;
      }
      return null;
    }
    return s;
  }

  protected abstract boolean doIsValid(MavenId id, MavenIndicesManager manager, ConvertContext context);

  @Override
  public String toString(@Nullable String s, @NotNull ConvertContext context) {
    return s;
  }

  @Override
  @NotNull
  public Collection<String> getVariants(@NotNull ConvertContext context) {
    DependencySearchService searchService = DependencySearchService.getInstance(context.getProject());
    MavenId id = MavenArtifactCoordinatesHelper.getId(context);

    MavenDomShortArtifactCoordinates coordinates = MavenArtifactCoordinatesHelper.getCoordinates(context);

    return selectStrategy(context).getVariants(id, searchService, coordinates);
  }

  protected abstract Set<String> doGetVariants(MavenId id, DependencySearchService searchService);

  @Override
  public PsiElement resolve(String o, @NotNull ConvertContext context) {
    MavenId id = MavenArtifactCoordinatesHelper.getId(context);

    PsiFile result = selectStrategy(context).resolve(id, context);
    return result != null ? result : super.resolve(o, context);
  }

  @Override
  public String getErrorMessage(@Nullable String s, @NotNull ConvertContext context) {
    return selectStrategy(context).getErrorMessage(s, context);
  }

  @Override
  public boolean isSoft(@NotNull DomElement element) {
    DomElement dependencyOrPluginElement = element.getParent();
    if (dependencyOrPluginElement instanceof MavenDomDependency) {
      DomElement dependencies = dependencyOrPluginElement.getParent();
      if (dependencies instanceof MavenDomDependencies) {
        if (dependencies.getParent() instanceof MavenDomDependencyManagement) {
          return true;
        }
      }
    }
    else if (dependencyOrPluginElement instanceof MavenDomPlugin) {
      DomElement pluginsElement = dependencyOrPluginElement.getParent();
      if (pluginsElement instanceof MavenDomPlugins) {
        if (pluginsElement.getParent() instanceof MavenDomPluginManagement) {
          return true;
        }
      }
    }

    return false;
  }

  @Nullable
  protected VirtualFile getMavenProjectFile(ConvertContext context) {
    PsiFile psiFile = context.getFile().getOriginalFile();
    return psiFile.getVirtualFile();
  }

  private ConverterStrategy selectStrategy(ConvertContext context) {
    DomElement parent = context.getInvocationElement().getParent();
    if (parent instanceof MavenDomProjectModel) {
      return new ProjectStrategy();
    }

    if (parent instanceof MavenDomParent) {
      return new ParentStrategy((MavenDomParent)parent);
    }

    if (parent instanceof MavenDomDependency) {
      return new DependencyStrategy((MavenDomDependency)parent);
    }

    if (parent instanceof MavenDomExclusion) {
      return new ExclusionStrategy();
    }

    if (parent instanceof MavenDomPlugin) {
      return new PluginOrExtensionStrategy(true);
    }

    if (parent instanceof MavenDomExtension) {
      return new PluginOrExtensionStrategy(false);
    }

    return new ConverterStrategy();
  }

  public static final class MyUpdateIndicesIntention implements IntentionAction {
    @Override
    @NotNull
    public String getFamilyName() {
      return MavenDomBundle.message("inspection.group");
    }

    @Override
    @NotNull
    public String getText() {
      return MavenDomBundle.message("fix.update.indices");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }


    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      MavenIndicesManager.getInstance(project).scheduleUpdateContentAll(true);
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return MavenUtil.isPomFile(project, file.getVirtualFile())
             && ContainerUtil.exists(MavenIndexUtils.getRemoteRepositoriesNoResolve(project), r -> !Objects.equals(r.getName(), "central"));
    }
  }

  private class ConverterStrategy {
    @Nls
    public String getErrorMessage(@Nullable String s, ConvertContext context) {
      return MavenDomBundle.message("artifact.0.not.found", MavenArtifactCoordinatesHelper.getId(context));
    }

    public boolean isValid(MavenId id, MavenIndicesManager manager, ConvertContext context) {
      return doIsValid(id, manager, context) || resolveBySpecifiedPath() != null;
    }

    public Set<String> getVariants(MavenId id, DependencySearchService searchService, MavenDomShortArtifactCoordinates coordinates) {
      return doGetVariants(id, searchService);
    }

    public PsiFile resolve(MavenId id, ConvertContext context) {
      PsiManager psiManager = context.getPsiManager();
      MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(psiManager.getProject());

      PsiFile result = resolveBySpecifiedPath();
      if (result != null) return result;

      result = resolveInProjects(id, projectsManager, context);
      if (result != null) return result;

      return resolveInLocalRepository(id, projectsManager, psiManager);
    }

    @Nullable
    protected PsiFile resolveBySpecifiedPath() {
      return null;
    }

    private PsiFile resolveInProjects(MavenId id, MavenProjectsManager projectsManager, ConvertContext context) {
      MavenProject project = resolveMavenProject(id, projectsManager, context);
      return project == null ? null : context.getPsiManager().findFile(project.getFile());
    }

    private static MavenProject resolveMavenProject(MavenId id, MavenProjectsManager projectsManager, ConvertContext context) {
      if (MavenConsumerPomUtil.isAutomaticVersionFeatureEnabled(context)) {
        return projectsManager.findSingleProjectInReactor(id);
      }
      else {
        return projectsManager.findProject(id);
      }
    }

    private static PsiFile resolveInLocalRepository(MavenId id, MavenProjectsManager projectsManager, PsiManager psiManager) {
      Path file = MavenUtil.getRepositoryFile(psiManager.getProject(), id, "pom", null);
      if (null == file) return null;
      VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByNioFile(file);
      if (virtualFile == null) return null;

      return psiManager.findFile(virtualFile);
    }
  }

  private class ProjectStrategy extends ConverterStrategy {
    @Override
    public PsiFile resolve(MavenId id, ConvertContext context) {
      return null;
    }

    @Override
    public boolean isValid(MavenId id, MavenIndicesManager manager, ConvertContext context) {
      return true;
    }
  }

  private class ParentStrategy extends ConverterStrategy {
    private final MavenDomParent myParent;

    ParentStrategy(MavenDomParent parent) {
      myParent = parent;
    }

    @Override
    public String getErrorMessage(@Nullable String s, ConvertContext context) {
      return MavenDomBundle.message("project.0.not.found", MavenArtifactCoordinatesHelper.getId(context));
    }

    @Override
    public PsiFile resolveBySpecifiedPath() {
      return myParent.getRelativePath().getValue();
    }
  }

  private class DependencyStrategy extends ConverterStrategy {
    private final MavenDomDependency myDependency;

    DependencyStrategy(MavenDomDependency dependency) {
      myDependency = dependency;
    }

    @Override
    public String getErrorMessage(@Nullable String s, ConvertContext context) {
      return MavenDomBundle.message("dependency.0.not.found", MavenArtifactCoordinatesHelper.getId(context));
    }

    @Override
    public PsiFile resolve(MavenId id, ConvertContext context) {
      PsiFile res = super.resolve(id, context);
      if (res != null) return res;

      DomElement parent = context.getInvocationElement().getParent();
      if (!(parent instanceof MavenDomDependency)) return null;

      DependencyConflictId dependencyId = DependencyConflictId.create((MavenDomDependency)parent);
      if (dependencyId == null) return null;

      MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(context.getProject());
      VirtualFile file = getMavenProjectFile(context);

      MavenProject mavenProject = file == null ? null : projectsManager.findProject(file);
      if (mavenProject != null) {
        MavenArtifact artifact = mavenProject.getDependencyArtifactIndex().findArtifacts(dependencyId);
        if (artifact != null && artifact.isResolved()) {
          return super.resolve(new MavenId(id.getGroupId(), id.getArtifactId(), artifact.getVersion()), context);
        }
      }

      if (StringUtil.isEmpty(id.getVersion())) {
        MavenDomDependency managedDependency = MavenDomProjectProcessorUtils.searchManagingDependency((MavenDomDependency)parent);
        if (managedDependency != null) {
          final GenericDomValue<String> managedDependencyArtifactId = managedDependency.getArtifactId();
          return RecursionManager.doPreventingRecursion(managedDependencyArtifactId, false, () -> {
            PsiElement res1 = new GenericDomValueReference(managedDependencyArtifactId).resolve();
            return res1 instanceof PsiFile ? (PsiFile)res1 : null;
          });
        }
      }

      return null;
    }

    @Override
    public PsiFile resolveBySpecifiedPath() {
      return myDependency.getSystemPath().getValue();
    }
  }

  private class ExclusionStrategy extends ConverterStrategy {
    @Override
    public PsiFile resolve(MavenId id, ConvertContext context) {
      return null;
    }

    @Override
    public boolean isValid(MavenId id, MavenIndicesManager manager, ConvertContext context) {
      return true;
    }
  }

  private class PluginOrExtensionStrategy extends ConverterStrategy {
    private final boolean myPlugin;

    PluginOrExtensionStrategy(boolean isPlugin) {
      myPlugin = isPlugin;
    }

    @Override
    public String getErrorMessage(@Nullable String s, ConvertContext context) {
      return myPlugin ? MavenDomBundle.message("plugin.0.not.found", MavenArtifactCoordinatesHelper.getId(context))
                      : MavenDomBundle.message("build.extension.0.not.found", MavenArtifactCoordinatesHelper.getId(context));
    }

    @Override
    public boolean isValid(MavenId id, MavenIndicesManager manager, ConvertContext context) {
      if (StringUtil.isEmpty(id.getGroupId())) {
        for (String each : MavenArtifactUtil.DEFAULT_GROUPS) {
          id = new MavenId(each, id.getArtifactId(), id.getVersion());
          if (super.isValid(id, manager, context)) return true;
        }
        return false;
      }
      return super.isValid(id, manager, context);
    }

    @Override
    public Set<String> getVariants(MavenId id, DependencySearchService searchService, MavenDomShortArtifactCoordinates coordinates) {
      if (StringUtil.isEmpty(id.getGroupId())) {
        Set<String> result = new HashSet<>();

        for (String each : MavenArtifactUtil.DEFAULT_GROUPS) {
          id = new MavenId(each, id.getArtifactId(), id.getVersion());
          result.addAll(super.getVariants(id, searchService, coordinates));
        }
        return result;
      }
      return super.getVariants(id, searchService, coordinates);
    }

    @Override
    public PsiFile resolve(MavenId id, ConvertContext context) {
      if (StringUtil.isEmpty(id.getGroupId())) {
        for (String each : MavenArtifactUtil.DEFAULT_GROUPS) {
          MavenId withGroupId = new MavenId(each, id.getArtifactId(), id.getVersion());
          PsiFile res = super.resolve(withGroupId, context);
          if (res != null) return res;
        }
      }

      PsiFile res = super.resolve(id, context);
      if (res != null) return res;

      MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(context.getProject());

      if (StringUtil.isEmpty(id.getGroupId())) {
        for (String each : MavenArtifactUtil.DEFAULT_GROUPS) {
          MavenId withGroupId = new MavenId(each, id.getArtifactId(), id.getVersion());
          res = super.resolve(withGroupId, context);
          if (res != null) return res;
        }
      }

      res = tryToResolveToImportedPlugins(id, context);
      if (res != null) return res;

      // Try to resolve to plugin with latest version
      PsiManager psiManager = context.getPsiManager();

      Path artifactFile = MavenArtifactUtil
        .getArtifactNioPath(projectsManager.getReposirotyPath(), id.getGroupId(), id.getArtifactId(), id.getVersion(), "pom");

      VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByNioFile(artifactFile);
      if (virtualFile != null) {
        return psiManager.findFile(virtualFile);
      }

      return null;
    }

    private @Nullable PsiFile tryToResolveToImportedPlugins(MavenId id, ConvertContext context) {
      MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(context.getProject());
      VirtualFile file = getMavenProjectFile(context);

      MavenProject mavenProject = file == null ? null : projectsManager.findProject(file);
      if (mavenProject != null) {
        for (MavenPlugin plugin : mavenProject.getPlugins()) {
          if (MavenArtifactUtil.isPluginIdEquals(id.getGroupId(), id.getArtifactId(), plugin.getGroupId(), plugin.getArtifactId())) {
            return super.resolve(plugin.getMavenId(), context);
          }
        }
      }
      return null;
    }
  }
}

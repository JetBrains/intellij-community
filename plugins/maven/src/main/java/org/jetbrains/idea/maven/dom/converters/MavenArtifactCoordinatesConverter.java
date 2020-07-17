/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.ResolvingConverter;
import com.intellij.util.xml.impl.GenericDomValueReference;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.DependencyConflictId;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils;
import org.jetbrains.idea.maven.dom.model.*;
import org.jetbrains.idea.maven.indices.MavenIndicesManager;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.reposearch.DependencySearchService;

import java.io.File;
import java.util.Collection;
import java.util.Set;


public abstract class MavenArtifactCoordinatesConverter extends ResolvingConverter<String> implements MavenDomSoftAwareConverter {
  @Override
  public String fromString(@Nullable @NonNls String s, ConvertContext context) {
    if (s == null) return null;

    MavenId id = MavenArtifactCoordinatesHelper.getId(context);
    MavenProjectIndicesManager manager = MavenProjectIndicesManager.getInstance(context.getProject());

    ConverterStrategy strategy = selectStrategy(context);
    boolean isValid = strategy.isValid(id, manager, context);
    if (!isValid) {
      File localRepository = MavenProjectsManager.getInstance(context.getProject()).getLocalRepository();
      VirtualFile file = MavenUtil.getRepositoryFile(context.getProject(), id, "pom", null);
      if (file != null) {
        File artifactFile = new File(file.getPath());
        MavenIndicesManager.getInstance().fixArtifactIndex(artifactFile, localRepository);
        return s;
      }
      return null;
    }
    return s;
  }

  protected abstract boolean doIsValid(MavenId id, MavenProjectIndicesManager manager, ConvertContext context);

  @Override
  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }

  @Override
  @NotNull
  public Collection<String> getVariants(ConvertContext context) {
    DependencySearchService searchService = DependencySearchService.getInstance(context.getProject());
    MavenId id = MavenArtifactCoordinatesHelper.getId(context);

    MavenDomShortArtifactCoordinates coordinates = MavenArtifactCoordinatesHelper.getCoordinates(context);

    return selectStrategy(context).getVariants(id, searchService, coordinates);
  }

  protected abstract Set<String> doGetVariants(MavenId id, DependencySearchService searchService);

  @Override
  public PsiElement resolve(String o, ConvertContext context) {
    MavenId id = MavenArtifactCoordinatesHelper.getId(context);

    PsiFile result = selectStrategy(context).resolve(id, context);
    return result != null ? result : super.resolve(o, context);
  }

  @Override
  public String getErrorMessage(@Nullable String s, ConvertContext context) {
    return selectStrategy(context).getContextName() + " '" + MavenArtifactCoordinatesHelper.getId(context) + "' not found";
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
  protected MavenProject findMavenProject(ConvertContext context) {
    PsiFile psiFile = context.getFile().getOriginalFile();
    VirtualFile file = psiFile.getVirtualFile();
    if (file == null) return null;

    return MavenProjectsManager.getInstance(psiFile.getProject()).findProject(file);
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

  public static class MyUpdateIndicesIntention implements IntentionAction {
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
      MavenProjectIndicesManager.getInstance(project).scheduleUpdateAll();
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return MavenUtil.isPomFile(project, file.getVirtualFile()) &&
             MavenProjectIndicesManager.getInstance(project).hasRemotesExceptCentral();

    }
  }

  private class ConverterStrategy {
    public String getContextName() {
      return "Artifact";
    }

    public boolean isValid(MavenId id, MavenProjectIndicesManager manager, ConvertContext context) {
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

      result = resolveInProjects(id, projectsManager, psiManager);
      if (result != null) return result;

      return resolveInLocalRepository(id, projectsManager, psiManager);
    }

    @Nullable
    protected PsiFile resolveBySpecifiedPath() {
      return null;
    }

    private PsiFile resolveInProjects(MavenId id, MavenProjectsManager projectsManager, PsiManager psiManager) {
      MavenProject project = resolveMavenProject(id, projectsManager);
      return project == null ? null : psiManager.findFile(project.getFile());
    }

    private MavenProject resolveMavenProject(MavenId id, MavenProjectsManager projectsManager) {
      if (MavenArtifactCoordinatesVersionConverter.isComsumerPomResolutionApplicable(projectsManager.getProject())) {
        return projectsManager.findSingleProjectInReactor(id);
      }  else {
        return projectsManager.findProject(id);
      }
    }

    private PsiFile resolveInLocalRepository(MavenId id, MavenProjectsManager projectsManager, PsiManager psiManager) {
      VirtualFile virtualFile = MavenUtil.getRepositoryFile(psiManager.getProject(), id, "pom", null);
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
    public boolean isValid(MavenId id, MavenProjectIndicesManager manager, ConvertContext context) {
      return true;
    }
  }

  private class ParentStrategy extends ConverterStrategy {
    private final MavenDomParent myParent;

    ParentStrategy(MavenDomParent parent) {
      myParent = parent;
    }

    @Override
    public String getContextName() {
      return "Project";
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
    public String getContextName() {
      return "Dependency";
    }

    @Override
    public PsiFile resolve(MavenId id, ConvertContext context) {
      PsiFile res = super.resolve(id, context);
      if (res != null) return res;

      DomElement parent = context.getInvocationElement().getParent();
      if (!(parent instanceof MavenDomDependency)) return null;

      DependencyConflictId dependencyId = DependencyConflictId.create((MavenDomDependency)parent);
      if (dependencyId == null) return null;

      MavenProject mavenProject = findMavenProject(context);
      if (mavenProject != null) {
        MavenArtifact artifact = mavenProject.getDependencyArtifactIndex().findArtifacts(dependencyId);
        if (artifact != null && artifact.isResolved()) {
          return super.resolve(new MavenId(id.getGroupId(), id.getArtifactId(), artifact.getVersion()), context);
        }
      }

      if (id.getVersion() == null) {
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
    public boolean isValid(MavenId id, MavenProjectIndicesManager manager, ConvertContext context) {
      return true;
    }
  }

  private class PluginOrExtensionStrategy extends ConverterStrategy {
    private final boolean myPlugin;

    PluginOrExtensionStrategy(boolean isPlugin) {
      myPlugin = isPlugin;
    }

    @Override
    public String getContextName() {
      return myPlugin ? "Plugin" : "Build Extension";
    }

    @Override
    public boolean isValid(MavenId id, MavenProjectIndicesManager manager, ConvertContext context) {
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
        Set<String> result = new THashSet<>();

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
      PsiFile res = super.resolve(id, context);
      if (res != null) return res;

      // Try to resolve to imported plugin
      MavenProject mavenProject = findMavenProject(context);
      if (mavenProject != null) {
        for (MavenPlugin plugin : mavenProject.getPlugins()) {
          if (MavenArtifactUtil.isPluginIdEquals(id.getGroupId(), id.getArtifactId(), plugin.getGroupId(), plugin.getArtifactId())) {
            return super.resolve(plugin.getMavenId(), context);
          }
        }
      }

      // Try to resolve to plugin with latest version
      PsiManager psiManager = context.getPsiManager();
      MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(psiManager.getProject());

      File artifactFile = MavenArtifactUtil
        .getArtifactFile(projectsManager.getLocalRepository(), id.getGroupId(), id.getArtifactId(), id.getVersion(), "pom");

      VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(artifactFile);
      if (virtualFile != null) {
        return psiManager.findFile(virtualFile);
      }

      return null;
    }
  }
}

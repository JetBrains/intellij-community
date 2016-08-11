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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xml.*;
import com.intellij.util.xml.impl.GenericDomValueReference;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.DependencyConflictId;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils;
import org.jetbrains.idea.maven.dom.model.*;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;

import java.io.File;
import java.util.Collection;
import java.util.Set;

public abstract class MavenArtifactCoordinatesConverter extends ResolvingConverter<String> implements MavenDomSoftAwareConverter {
  public String fromString(@Nullable @NonNls String s, ConvertContext context) {
    if (s == null) return null;

    MavenId id = MavenArtifactCoordinatesHelper.getId(context);
    MavenProjectIndicesManager manager = MavenProjectIndicesManager.getInstance(context.getProject());

    return selectStrategy(context).isValid(id, manager, context) ? s : null;
  }

  protected abstract boolean doIsValid(MavenId id, MavenProjectIndicesManager manager, ConvertContext context);

  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }

  @NotNull
  public Collection<String> getVariants(ConvertContext context) {
    MavenProjectIndicesManager manager = MavenProjectIndicesManager.getInstance(context.getProject());
    MavenId id = MavenArtifactCoordinatesHelper.getId(context);

    MavenDomShortArtifactCoordinates coordinates = MavenArtifactCoordinatesHelper.getCoordinates(context);

    return selectStrategy(context).getVariants(id, manager, coordinates);
  }

  protected abstract Set<String> doGetVariants(MavenId id, MavenProjectIndicesManager manager);

  @Override
  public PsiElement resolve(String o, ConvertContext context) {
    MavenId id = MavenArtifactCoordinatesHelper.getId(context);

    PsiFile result = selectStrategy(context).resolve(id, context);
    return result != null ? result : super.resolve(o, context);
  }

  @Override
  public String getErrorMessage(@Nullable String s, ConvertContext context) {
    return selectStrategy(context).getContextName() + " '''" + MavenArtifactCoordinatesHelper.getId(context) + "''' not found";
  }

  @Override
  public LocalQuickFix[] getQuickFixes(ConvertContext context) {
    return ArrayUtil.append(super.getQuickFixes(context), new MyUpdateIndicesFix());
  }

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

  private static class MyUpdateIndicesFix implements LocalQuickFix {
    @NotNull
    public String getFamilyName() {
      return MavenDomBundle.message("inspection.group");
    }

    @NotNull
    public String getName() {
      return MavenDomBundle.message("fix.update.indices");
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      MavenProjectIndicesManager.getInstance(project).scheduleUpdateAll();
    }
  }

  private class ConverterStrategy {
    public String getContextName() {
      return "Artifact";
    }

    public boolean isValid(MavenId id, MavenProjectIndicesManager manager, ConvertContext context) {
      return doIsValid(id, manager, context) || resolveBySpecifiedPath() != null;
    }

    public Set<String> getVariants(MavenId id, MavenProjectIndicesManager manager, MavenDomShortArtifactCoordinates coordinates) {
      return doGetVariants(id, manager);
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
      MavenProject project = projectsManager.findProject(id);
      return project == null ? null : psiManager.findFile(project.getFile());
    }

    private PsiFile resolveInLocalRepository(MavenId id, MavenProjectsManager projectsManager, PsiManager psiManager) {
      File file = makeLocalRepositoryFile(id, projectsManager.getLocalRepository());
      if (file == null) return null;

      VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
      if (virtualFile == null) return null;

      return psiManager.findFile(virtualFile);
    }

    private File makeLocalRepositoryFile(MavenId id, File localRepository) {
      String relPath = (StringUtil.notNullize(id.getGroupId(), "null")).replace(".", "/");

      relPath += "/" + id.getArtifactId();
      relPath += "/" + id.getVersion();
      relPath += "/" + id.getArtifactId() + "-" + id.getVersion() + ".pom";

      return new File(localRepository, relPath);
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

    public ParentStrategy(MavenDomParent parent) {
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

    public DependencyStrategy(MavenDomDependency dependency) {
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

    @Override
    public Set<String> getVariants(MavenId id, MavenProjectIndicesManager manager, MavenDomShortArtifactCoordinates coordinates) {
      if (StringUtil.isEmpty(id.getGroupId())) {
        Set<String> result = new THashSet<>();

        for (String each : manager.getGroupIds()) {
          id = new MavenId(each, id.getArtifactId(), id.getVersion());
          result.addAll(super.getVariants(id, manager, coordinates));
        }

        return result;
      }
      return super.getVariants(id, manager, coordinates);
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

    public PluginOrExtensionStrategy(boolean isPlugin) {
      myPlugin = isPlugin;
    }

    @Override
    public String getContextName() {
      return myPlugin ? "Plugin" : "Build Extension";
    }

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
    public Set<String> getVariants(MavenId id, MavenProjectIndicesManager manager, MavenDomShortArtifactCoordinates coordinates) {
      if (StringUtil.isEmpty(id.getGroupId())) {
        Set<String> result = new THashSet<>();

        for (String each : getGroupIdVariants(manager, coordinates)) {
          id = new MavenId(each, id.getArtifactId(), id.getVersion());
          result.addAll(super.getVariants(id, manager, coordinates));
        }
        return result;
      }
      return super.getVariants(id, manager, coordinates);
    }

    private String[] getGroupIdVariants(MavenProjectIndicesManager manager, MavenDomShortArtifactCoordinates coordinates) {
      if (DomUtil.hasXml(coordinates.getGroupId())) {
        Set<String> strings = manager.getGroupIds();
        return ArrayUtil.toStringArray(strings);
      }
      return MavenArtifactUtil.DEFAULT_GROUPS;
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

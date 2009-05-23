package org.jetbrains.idea.maven.dom;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.*;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenId;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;

import java.io.File;
import java.util.Collection;
import java.util.Set;

public abstract class MavenArtifactCoordinatesConverter extends ResolvingConverter<String> {
  public String fromString(@Nullable @NonNls String s, ConvertContext context) {
    if (s == null) return null;

    MavenId id = MavenArtifactCoordinatesHelper.getId(context);
    MavenProjectIndicesManager manager = MavenProjectIndicesManager.getInstance(getProject(context));

    return selectStrategy(context).isValid(id, manager, context) ? s : null;
  }

  protected abstract boolean doIsValid(MavenId id, MavenProjectIndicesManager manager, ConvertContext context);

  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }

  @NotNull
  public Collection<String> getVariants(ConvertContext context) {
    MavenProjectIndicesManager manager = MavenProjectIndicesManager.getInstance(getProject(context));
    MavenId id = MavenArtifactCoordinatesHelper.getId(context);
    return selectStrategy(context).getVariants(id, manager);
  }

  protected abstract Set<String> doGetVariants(MavenId id, MavenProjectIndicesManager manager);

  @Override
  public PsiElement resolve(String o, ConvertContext context) {
    Project p = getProject(context);
    MavenId id = MavenArtifactCoordinatesHelper.getId(context);

    PsiFile result = selectStrategy(context).resolve(p, id);
    return result != null ? result : super.resolve(o, context);
  }

  private Project getProject(ConvertContext context) {
    return context.getFile().getProject();
  }

  @Override
  public String getErrorMessage(@Nullable String s, ConvertContext context) {
    return selectStrategy(context).getContextName() + " '''" + MavenArtifactCoordinatesHelper.getId(context) + "''' not found";
  }

  @Override
  public LocalQuickFix[] getQuickFixes(ConvertContext context) {
    return ArrayUtil.append(super.getQuickFixes(context), new MyUpdateIndicesFix());
  }

  private ConverterStrategy selectStrategy(ConvertContext context) {
    if (MavenDomUtil.getImmediateParent(context, MavenDomProjectModel.class) != null) {
      return new ProjectStrategy();
    }

    MavenDomParent parent = MavenDomUtil.getImmediateParent(context, MavenDomParent.class);
    if (parent != null) {
      return new ParentStrategy(parent);
    }

    MavenDomDependency dependency = MavenDomUtil.getImmediateParent(context, MavenDomDependency.class);
    if (dependency != null) {
      return new DependencyStrategy(dependency);
    }

    if (MavenDomUtil.getImmediateParent(context, MavenDomExclusion.class) != null ) {
      return new ExclusionStrategy();
    }

    if (MavenDomUtil.getImmediateParent(context, MavenDomPlugin.class) != null ) {
      return new PluginOrExtensionStrategy(true);
    }

    if (MavenDomUtil.getImmediateParent(context, MavenDomExtension.class) != null) {
      return new PluginOrExtensionStrategy(false);
    }

    return new ConverterStrategy();
  }

  private class MyUpdateIndicesFix implements LocalQuickFix {
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

    public Set<String> getVariants(MavenId id, MavenProjectIndicesManager manager) {
      return doGetVariants(id, manager);
    }

    public PsiFile resolve(Project project, MavenId id) {
      MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);
      PsiManager psiManager = PsiManager.getInstance(project);

      PsiFile result = resolveBySpecifiedPath();
      if (result != null) return result;

      result = resolveInProjects(id, projectsManager, psiManager);
      if (result != null) return result;

      return resolveInLocalRepository(id, projectsManager, psiManager);
    }

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

    protected File makeLocalRepositoryFile(MavenId id, File localRepostory) {
      String relPath = ("" + id.groupId).replace(".", "/");

      relPath += "/" + id.artifactId;
      relPath += "/" + id.version;
      relPath += "/" + id.artifactId + "-" + id.version + ".pom";

      return new File(localRepostory, relPath);
    }
  }

  private class ProjectStrategy extends ConverterStrategy {
    @Override
    public PsiFile resolve(Project project, MavenId id) {
      return null;
    }

    @Override
    public boolean isValid(MavenId id, MavenProjectIndicesManager manager, ConvertContext context) {
      return true;
    }
  }

  private class ParentStrategy extends ConverterStrategy {
    private MavenDomParent myParent;

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
    private MavenDomDependency myDependency;

    public DependencyStrategy(MavenDomDependency dependency) {
      myDependency = dependency;
    }

    @Override
    public String getContextName() {
      return "Dependency";
    }

    @Override
    public PsiFile resolveBySpecifiedPath() {
      return myDependency.getSystemPath().getValue();
    }
  }

  private class ExclusionStrategy extends ConverterStrategy {
    @Override
    public PsiFile resolve(Project project, MavenId id) {
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
      return myPlugin ? "Plugin " : "Build Extension";
    }

    public boolean isValid(MavenId id, MavenProjectIndicesManager manager, ConvertContext context) {
      if (StringUtil.isEmpty(id.groupId)) {
        for (String each : MavenArtifactUtil.DEFAULT_GROUPS) {
          id.groupId = each;
          if (super.isValid(id, manager, context)) return true;
        }
        return false;
      }
      return super.isValid(id, manager, context);
    }

    @Override
    public Set<String> getVariants(MavenId id, MavenProjectIndicesManager manager) {
      if (StringUtil.isEmpty(id.groupId)) {
        Set<String> result = new THashSet<String>();
        for (String each : MavenArtifactUtil.DEFAULT_GROUPS) {
          id.groupId = each;
          result.addAll(super.getVariants(id, manager));
        }
        return result;
      }
      return super.getVariants(id, manager);
    }

    @Override
    protected File makeLocalRepositoryFile(MavenId id, File localRepository) {
      return MavenArtifactUtil.getArtifactFile(localRepository, id.groupId, id.artifactId, id.version, "pom");
    }
  }
}

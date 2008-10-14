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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.Dependency;
import org.jetbrains.idea.maven.dom.model.Extension;
import org.jetbrains.idea.maven.dom.model.MavenParent;
import org.jetbrains.idea.maven.dom.model.Plugin;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import org.jetbrains.idea.maven.project.MavenProjectModel;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;
import org.jetbrains.idea.maven.utils.MavenId;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
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
  public LocalQuickFix[] getQuickFixes(ConvertContext context) {
    return ArrayUtil.append(super.getQuickFixes(context), new MyUpdateIndicesFix());
  }

  private ConverterStrategy selectStrategy(ConvertContext context) {
    Dependency dependency = MavenDomUtil.getImmediateParent(context, Dependency.class);
    if (dependency != null) {
      return new DependencyStrategy(dependency);
    }
    if (MavenDomUtil.getImmediateParent(context, Plugin.class) != null
        || MavenDomUtil.getImmediateParent(context, Extension.class) != null) {
      return new PluginOrExtensionStrategy();
    }
    MavenParent parent = MavenDomUtil.getImmediateParent(context, MavenParent.class);
    if (parent != null) {
      return new ParentStrategy(parent);
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
    public boolean isValid(MavenId id, MavenProjectIndicesManager manager, ConvertContext context) {
      return doIsValid(id, manager, context);
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
      MavenProjectModel Project = projectsManager.findProject(id);
      return Project == null ? null : psiManager.findFile(Project.getFile());
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

  private class ParentStrategy extends ConverterStrategy {
    private MavenParent myParent;

    public ParentStrategy(MavenParent parent) {
      myParent = parent;
    }

    @Override
    public PsiFile resolveBySpecifiedPath() {
      return myParent.getRelativePath().getValue();
    }
  }

  private class DependencyStrategy extends ConverterStrategy {
    private Dependency myDependency;

    public DependencyStrategy(Dependency dependency) {
      myDependency = dependency;
    }

    public boolean isValid(MavenId id, MavenProjectIndicesManager manager, ConvertContext context) {
      if ("system".equals(myDependency.getScope().getStringValue())) return true;
      return super.isValid(id, manager, context);
    }

    @Override
    public PsiFile resolveBySpecifiedPath() {
      return myDependency.getSystemPath().getValue();
    }
  }

  private class PluginOrExtensionStrategy extends ConverterStrategy {
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
        Set<String> result = new HashSet<String>();
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
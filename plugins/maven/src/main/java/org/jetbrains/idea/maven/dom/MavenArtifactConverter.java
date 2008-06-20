package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.MavenLog;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.dom.model.Dependency;
import org.jetbrains.idea.maven.dom.model.MavenModel;
import org.jetbrains.idea.maven.dom.model.MavenParent;
import org.jetbrains.idea.maven.project.MavenProjectModel;
import org.jetbrains.idea.maven.repository.MavenIndexException;
import org.jetbrains.idea.maven.repository.MavenIndicesManager;
import org.jetbrains.idea.maven.state.MavenProjectsManager;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public abstract class MavenArtifactConverter extends ResolvingConverter<String> {
  public String fromString(@Nullable @NonNls String s, ConvertContext context) {
    try {
      return isValid(MavenIndicesManager.getInstance(), getId(context), getSpecifiedFilePath(context)) ? s : null;
    }
    catch (MavenIndexException e) {
      MavenLog.info(e);
      return s;
    }
  }

  protected abstract boolean isValid(MavenIndicesManager manager, MavenId id, String specifiedPath) throws MavenIndexException;

  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }

  @NotNull
  public Collection<String> getVariants(ConvertContext context) {
    try {
      return getVariants(MavenIndicesManager.getInstance(), getId(context));
    }
    catch (MavenIndexException e) {
      MavenLog.info(e);
      return Collections.emptyList();
    }
  }

  protected abstract Set<String> getVariants(MavenIndicesManager manager, MavenId id) throws MavenIndexException;

  protected MavenId getId(ConvertContext context) {
    Dependency dep = getMavenDependency(context);
    if (dep != null) {
      return createId(context, dep.getGroupId(), dep.getArtifactId(), dep.getVersion());
    }
    MavenParent parent = getMavenParent(context);
    if (parent != null) {
      return createId(context, parent.getGroupId(), parent.getArtifactId(), parent.getVersion());
    }
    throw new RuntimeException("unknown context: " + context.getInvocationElement().getParent());
  }

  private MavenId createId(ConvertContext context,
                           GenericDomValue<String> groupId,
                           GenericDomValue<String> artifactId,
                           GenericDomValue<String> version) {
    return new MavenId(resolveProperties(groupId.getStringValue(), context),
                       resolveProperties(artifactId.getStringValue(), context),
                       resolveProperties(version.getStringValue(), context));
  }

  private String resolveProperties(String text, ConvertContext context) {
    if (StringUtil.isEmpty(text)) return "";

    DomFileElement<MavenModel> dom = context.getInvocationElement().getRoot();
    VirtualFile virtualFile = dom.getOriginalFile().getVirtualFile();
    return PropertyResolver.resolve(text, virtualFile, dom);
  }

  private VirtualFile getSpecifiedFile(ConvertContext context) {
    String path = getSpecifiedFilePath(context);
    if (path == null) return null;
    return LocalFileSystem.getInstance().findFileByPath(path);
  }

  private String getSpecifiedFilePath(ConvertContext context) {
    MavenParent parent = getMavenParent(context);
    if (parent != null) {
      String relPath = parent.getRelativePath().getStringValue();
      if (relPath == null) return null;
      String basePath = context.getFile().getVirtualFile().getParent().getPath();
      return FileUtil.toSystemIndependentName(new File(basePath, relPath).getPath());
    }

    Dependency dep = getMavenDependency(context);
    if (dep != null) {
      return dep.getSystemPath().getStringValue();
    }

    return null;
  }

  @Override
  public PsiElement resolve(String o, ConvertContext context) {
    Project p = context.getFile().getProject();
    MavenProjectsManager manager = MavenProjectsManager.getInstance(p);

    MavenId id = getId(context);

    MavenProjectModel project = manager.findProject(id);
    if (project != null) {
      return PsiManager.getInstance(p).findFile(project.getFile());
    }

    VirtualFile f = getSpecifiedFile(context);
    if (f != null) {
      return PsiManager.getInstance(p).findFile(f);
    }

    File file = new File(manager.getMavenCoreSettings().getEffectiveLocalRepository(),
                         assembleArtifactFile(context, id));
    f = LocalFileSystem.getInstance().findFileByIoFile(file);
    if (f != null) {
      return PsiManager.getInstance(p).findFile(f);
    }

    return super.resolve(o, context);
  }

  private String assembleArtifactFile(ConvertContext context, MavenId id) {
    String type = "pom";

    Dependency dep = getMavenDependency(context);
    if (dep != null) {
      type = dep.getType().getStringValue();
      if (type == null) type = "jar";
    }

    String result = ("" + id.groupId).replace(".", "/");
    result += "/" + id.artifactId;
    result += "/" + id.version;
    result += "/" + id.artifactId + "-" + id.version + "." + type;

    return result;
  }

  protected MavenParent getMavenParent(ConvertContext context) {
    DomElement parentElement = context.getInvocationElement().getParent();
    return parentElement instanceof MavenParent ? (MavenParent)parentElement : null;
  }

  protected Dependency getMavenDependency(ConvertContext context) {
    DomElement parentElement = context.getInvocationElement().getParent();
    return parentElement instanceof Dependency ? (Dependency)parentElement : null;
  }
}
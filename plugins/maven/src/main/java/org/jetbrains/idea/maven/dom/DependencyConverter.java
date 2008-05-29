package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.MavenLog;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.dom.model.Dependency;
import org.jetbrains.idea.maven.dom.model.MavenModel;
import org.jetbrains.idea.maven.repository.MavenIndexException;
import org.jetbrains.idea.maven.repository.MavenIndicesManager;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public abstract class DependencyConverter extends ResolvingConverter<String> {
  public String fromString(@Nullable @NonNls String s, ConvertContext context) {
    try {
      Project p = context.getModule().getProject();
      return isValid(MavenIndicesManager.getInstance(p), getDependencyId(context)) ? s : null;
    }
    catch (MavenIndexException e) {
      MavenLog.LOG.info(e);
      return s;
    }
  }

  protected abstract boolean isValid(MavenIndicesManager manager, MavenId dependencyId) throws MavenIndexException;

  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }

  @NotNull
  public Collection<String> getVariants(ConvertContext context) {
    try {
      Project p = context.getModule().getProject();
      return getVariants(MavenIndicesManager.getInstance(p), getDependencyId(context));
    }
    catch (MavenIndexException e) {
      MavenLog.LOG.info(e);
      return Collections.emptyList();
    }
  }

  protected abstract Set<String> getVariants(MavenIndicesManager manager, MavenId dependencyId) throws MavenIndexException;

  private MavenId getDependencyId(ConvertContext context) {
    Project p = context.getModule().getProject();
    Dependency dep = (Dependency)context.getInvocationElement().getParent();

    MavenId result = new MavenId();

    result.groupId = resolveProperties(dep.getGroupId().getStringValue(), context);
    result.artifactId = resolveProperties(dep.getArtifactId().getStringValue(), context);
    result.version = resolveProperties(dep.getVersion().getStringValue(), context);

    return result;
  }

  private String resolveProperties(String text, ConvertContext context) {
    if (StringUtil.isEmpty(text)) return "";

    DomFileElement<MavenModel> dom = context.getInvocationElement().getRoot();
    VirtualFile virtualFile = dom.getOriginalFile().getVirtualFile();
    return PropertyResolver.resolve(text, virtualFile, dom);
  }
}
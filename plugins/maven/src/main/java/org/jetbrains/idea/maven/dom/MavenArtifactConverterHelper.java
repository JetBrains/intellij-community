package org.jetbrains.idea.maven.dom;

import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.dom.model.Dependency;
import org.jetbrains.idea.maven.dom.model.MavenParent;
import org.jetbrains.idea.maven.dom.model.Plugin;

public class MavenArtifactConverterHelper {
  public static MavenId getId(ConvertContext context) {
    Dependency dep = getMavenDependency(context);
    if (dep != null) {
      return createId(dep.getGroupId(), dep.getArtifactId(), dep.getVersion());
    }
    Plugin plugin = getMavenPlugin(context);
    if (plugin != null) {
      return createId(plugin.getGroupId(), plugin.getArtifactId(), plugin.getVersion());
    }
    MavenParent parent = getMavenParent(context);
    if (parent != null) {
      return createId(parent.getGroupId(), parent.getArtifactId(), parent.getVersion());
    }
    throw new RuntimeException("unknown context: " + context.getInvocationElement().getParent());
  }

  private static MavenId createId(GenericDomValue<String> groupId,
                                  GenericDomValue<String> artifactId,
                                  GenericDomValue<String> version) {
    return new MavenId(resolveProperties(groupId),
                       resolveProperties(artifactId),
                       resolveProperties(version));
  }

  public static String resolveProperties(GenericDomValue<String> value) {
    String result = PropertyResolver.resolve(value);
    return result == null ? "" : result;
  }

  public static MavenParent getMavenParent(ConvertContext context) {
    DomElement parentElement = context.getInvocationElement().getParent();
    return parentElement instanceof MavenParent ? (MavenParent)parentElement : null;
  }

  public static Dependency getMavenDependency(ConvertContext context) {
    DomElement parentElement = context.getInvocationElement().getParent();
    return parentElement instanceof Dependency ? (Dependency)parentElement : null;
  }

  public static Plugin getMavenPlugin(ConvertContext context) {
    DomElement parentElement = context.getInvocationElement().getParent();
    return parentElement instanceof Plugin ? (Plugin)parentElement : null;
  }
}

package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.idea.maven.dom.model.Plugin;
import org.jetbrains.idea.maven.dom.plugin.MavenPluginModel;
import org.jetbrains.idea.maven.core.util.MavenArtifactUtil;
import org.jetbrains.idea.maven.state.MavenProjectsManager;

import java.io.File;

public class MavenPluginDomUtil {
  public static MavenPluginModel getMavenPlugin(DomElement element) {
    Project p = element.getXmlElement().getProject();

    Plugin pluginElement = element.getParentOfType(Plugin.class, false);
    if (pluginElement == null) return null;

    VirtualFile pluginXmlFile = getPluginXmlFile(p, pluginElement);
    if (pluginXmlFile == null) return null;

    return getMavenPluginElement(p, pluginXmlFile);
  }

  private static VirtualFile getPluginXmlFile(Project p, Plugin pluginElement) {
    String groupId = resolveProperties(pluginElement.getGroupId());
    String artifactId = resolveProperties(pluginElement.getArtifactId());
    String version = resolveProperties(pluginElement.getVersion());

    File file = MavenArtifactUtil.getArtifactFile(MavenProjectsManager.getInstance(p).getLocalRepository(),
                                                    groupId, artifactId, version, "jar");
    VirtualFile pluginFile = LocalFileSystem.getInstance().findFileByIoFile(file);
    if (pluginFile == null) return null;

    VirtualFile pluginJarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(pluginFile);
    if (pluginJarRoot == null) return null;
    return pluginJarRoot.findFileByRelativePath(MavenArtifactUtil.MAVEN_PLUGIN_DESCRIPTOR);
  }

  private static String resolveProperties(GenericDomValue<String> value) {
    return PropertyResolver.resolve(value);
  }

  private static MavenPluginModel getMavenPluginElement(Project p, VirtualFile pluginXml) {
    PsiFile psiFile = PsiManager.getInstance(p).findFile(pluginXml);
    return DomManager.getDomManager(p).getFileElement((XmlFile)psiFile, MavenPluginModel.class).getRootElement();
  }
}

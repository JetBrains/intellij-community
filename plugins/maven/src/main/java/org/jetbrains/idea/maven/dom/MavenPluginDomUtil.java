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
import org.jetbrains.idea.maven.indices.MavenPluginInfoReader;
import org.jetbrains.idea.maven.indices.MavenPluginsRepository;

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

    String pluginPath = MavenPluginsRepository.getInstance(p).findPluginPath(groupId, artifactId, version);
    if (pluginPath == null) return null;

    VirtualFile pluginFile = LocalFileSystem.getInstance().findFileByPath(pluginPath);
    if (pluginFile == null) return null;

    VirtualFile pluginJarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(pluginFile);
    if (pluginJarRoot == null) return null;
    return pluginJarRoot.findFileByRelativePath(MavenPluginInfoReader.MAVEN_PLUGIN_DESCRIPTOR);
  }

  private static String resolveProperties(GenericDomValue<String> value) {
    return PropertyResolver.resolve(value);
  }

  private static MavenPluginModel getMavenPluginElement(Project p, VirtualFile pluginXml) {
    PsiFile psiFile = PsiManager.getInstance(p).findFile(pluginXml);
    return DomManager.getDomManager(p).getFileElement((XmlFile)psiFile, MavenPluginModel.class).getRootElement();
  }
}

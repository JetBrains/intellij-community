package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xml.DomElement;
import org.jetbrains.idea.maven.dom.model.MavenDomPlugin;
import org.jetbrains.idea.maven.dom.plugin.MavenDomPluginModel;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.io.File;

public class MavenPluginDomUtil {
  public static MavenDomPluginModel getMavenPluginModel(DomElement element) {
    Project p = element.getXmlElement().getProject();

    MavenDomPlugin pluginElement = element.getParentOfType(MavenDomPlugin.class, false);
    if (pluginElement == null) return null;

    VirtualFile pluginXmlFile = getPluginXmlFile(p, pluginElement);
    if (pluginXmlFile == null) return null;

    return MavenDomUtil.getMavenDomModel(p, pluginXmlFile, MavenDomPluginModel.class);
  }

  private static VirtualFile getPluginXmlFile(Project p, MavenDomPlugin pluginElement) {
    String groupId = pluginElement.getGroupId().getStringValue();
    String artifactId = pluginElement.getArtifactId().getStringValue();
    String version = pluginElement.getVersion().getStringValue();

    File file = MavenArtifactUtil.getArtifactFile(MavenProjectsManager.getInstance(p).getLocalRepository(),
                                                    groupId, artifactId, version, "jar");
    VirtualFile pluginFile = LocalFileSystem.getInstance().findFileByIoFile(file);
    if (pluginFile == null) return null;

    VirtualFile pluginJarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(pluginFile);
    if (pluginJarRoot == null) return null;
    return pluginJarRoot.findFileByRelativePath(MavenArtifactUtil.MAVEN_PLUGIN_DESCRIPTOR);
  }
}

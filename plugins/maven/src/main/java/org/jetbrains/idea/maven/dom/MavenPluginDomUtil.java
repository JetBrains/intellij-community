/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
    VirtualFile pluginFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    if (pluginFile == null) return null;

    VirtualFile pluginJarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(pluginFile);
    if (pluginJarRoot == null) return null;
    return pluginJarRoot.findFileByRelativePath(MavenArtifactUtil.MAVEN_PLUGIN_DESCRIPTOR);
  }
}

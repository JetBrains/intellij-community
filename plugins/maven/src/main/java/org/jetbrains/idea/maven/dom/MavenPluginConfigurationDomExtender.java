package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomExtender;
import com.intellij.util.xml.reflect.DomExtension;
import com.intellij.util.xml.reflect.DomExtensionsRegistrar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.model.Configuration;
import org.jetbrains.idea.maven.dom.model.Plugin;
import org.jetbrains.idea.maven.dom.model.PluginExecution;
import org.jetbrains.idea.maven.dom.plugin.MavenPluginModel;
import org.jetbrains.idea.maven.dom.plugin.Mojo;
import org.jetbrains.idea.maven.dom.plugin.Parameter;
import org.jetbrains.idea.maven.repository.MavenPluginsRepository;

import java.util.*;

public class MavenPluginConfigurationDomExtender extends DomExtender<Configuration> {
  public static final Key<Parameter> PLUGIN_PARAMETER_KEY = Key.create("MavenPluginConfigurationDomExtender.PLUGIN_PARAMETER_KEY");

  public Object[] registerExtensions(@NotNull Configuration c, @NotNull DomExtensionsRegistrar r) {
    Project p = c.getXmlElement().getProject();

    DomElement parent = c.getParent();

    Plugin pluginElement;
    PluginExecution executionElement = null;
    if (parent instanceof PluginExecution) {
      executionElement = (PluginExecution)parent;
      pluginElement = (Plugin)parent.getParent().getParent();
    } else  {
      pluginElement = (Plugin)parent;
    }

    MavenPluginModel pluginModel = getMavenPlugin(p, pluginElement);
    if (pluginModel == null) return ArrayUtil.EMPTY_OBJECT_ARRAY;

    for (Parameter each : collectParameters(pluginModel, executionElement)) {
      registerPluginParameter(r, each);
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  private MavenPluginModel getMavenPlugin(Project p, Plugin pluginElement) {
    VirtualFile pluginXmlFile = getPluginXmlFile(p, pluginElement);
    if (pluginXmlFile == null) return null;
    return getMavenPluginElement(p, pluginXmlFile);
  }

  private VirtualFile getPluginXmlFile(Project p, Plugin pluginElement) {
    String groupId = pluginElement.getGroupId().getStringValue();
    String artifactId = pluginElement.getArtifactId().getStringValue();
    String version = pluginElement.getVersion().getStringValue();

    String pluginPath = MavenPluginsRepository.getInstance(p).findPluginPath(groupId, artifactId, version);
    if (pluginPath == null) return null;

    VirtualFile pluginFile = LocalFileSystem.getInstance().findFileByPath(pluginPath);
    if (pluginFile == null) return null;

    VirtualFile pluginJarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(pluginFile);
    return pluginJarRoot.findFileByRelativePath(MavenPluginsRepository.MAVEN_PLUGIN_DESCRIPTOR);
  }

  private MavenPluginModel getMavenPluginElement(Project p, VirtualFile pluginXml) {
    PsiFile psiFile =  PsiManager.getInstance(p).findFile(pluginXml);
    return DomManager.getDomManager(p).getFileElement((XmlFile)psiFile, MavenPluginModel.class).getRootElement();
  }

  private Collection<Parameter> collectParameters(MavenPluginModel pluginModel, PluginExecution executionElement) {
    List<String> selectedGoals = null;
    if (executionElement != null) {
      selectedGoals = new ArrayList<String>();
      for (GenericDomValue<String> goal : executionElement.getGoals().getGoals()) {
        selectedGoals.add(goal.getStringValue());
      }
    }

    Map <String, Parameter> namesWithParameters = new HashMap<String, Parameter>();

    for (Mojo eachMojo : pluginModel.getMojos().getMojos()) {
      String goal = eachMojo.getGoal().getStringValue();
      if (selectedGoals == null || selectedGoals.contains(goal)) {
        for (Parameter eachParameter : eachMojo.getParameters().getParameters()) {
          if (!eachParameter.getEditable().getValue()) continue;

          String name = eachParameter.getName().getStringValue();
          if (namesWithParameters.containsKey(name)) continue;
          namesWithParameters.put(name, eachParameter);
        }
      }
    }

    return namesWithParameters.values();
  }

  private void registerPluginParameter(DomExtensionsRegistrar r, Parameter each) {
    DomExtension e = r.registerFixedNumberChildExtension(new XmlName(each.getName().getStringValue()), Parameter.class);
    e.putUserData(DomExtension.KEY_DECLARATION, each);
    each.getXmlElement().putUserData(PLUGIN_PARAMETER_KEY, each);
  }
}
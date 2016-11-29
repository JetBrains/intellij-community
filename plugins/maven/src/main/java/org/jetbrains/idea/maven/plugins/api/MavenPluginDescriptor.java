/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.plugins.api;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.Required;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.idea.maven.dom.model.MavenDomConfiguration;
import org.jetbrains.idea.maven.dom.model.MavenDomGoal;
import org.jetbrains.idea.maven.dom.model.MavenDomPlugin;
import org.jetbrains.idea.maven.dom.model.MavenDomPluginExecution;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class MavenPluginDescriptor extends AbstractExtensionPointBean {

  public static final ExtensionPointName<MavenPluginDescriptor> EP_NAME =
    new ExtensionPointName<>("org.jetbrains.idea.maven.pluginDescriptor");

  // Map artifactId -> groupId -> goal -> List<MavenPluginDescriptor>
  public static volatile Map<String, Map<String, Map<String, List<MavenPluginDescriptor>>>> ourDescriptorsMap;

  @Attribute("goal")
  public String goal;

  @Attribute("mavenId")
  @Required
  public String mavenId;

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public Param[] params;

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public ModelProperty[] properties;

  @Attribute("propertyGenerator")
  public String propertyGenerator;

  @Tag("property")
  public static class ModelProperty {
    @Attribute("name")
    @Required
    public String name;

    @Attribute
    public String value;

    @Attribute
    public boolean insideConfigurationOnly;
  }

  @Tag("param")
  public static class Param {

    @Attribute("name")
    @Required
    public String name;

    /**
     * Class name of reference provider. The reference provider must implement MavenParamReferenceProvider or PsiReferenceProvider.
     */
    @Attribute("refProvider")
    public String refProvider;

    @Attribute("values")
    public String values;

    @Attribute("soft")
    public Boolean soft;

    /**
     * Disallow to add standard maven references to parameter like <delimiter>$$</delimiter>, see MavenPropertyPsiReferenceProvider
     */
    @Attribute("disableReferences")
    public Boolean disableReferences;

    /**
     * Language to inject.
     */
    @Attribute("language")
    public String language;

    /**
     * Class of type org.jetbrains.idea.maven.plugins.api.MavenParamLanguageProvider
     */
    @Attribute("languageProvider")
    public String languageProvider;

    @Attribute("languageInjectionPrefix")
    public String languageInjectionPrefix;

    @Attribute("languageInjectionSuffix")
    public String languageInjectionSuffix;
  }

  public static Pair<String, String> parsePluginId(String mavenId) {
    int idx = mavenId.indexOf(':');
    if (idx <= 0 || idx == mavenId.length() - 1 || mavenId.lastIndexOf(':') != idx) {
      throw new RuntimeException("Failed to parse mavenId: " + mavenId + " (mavenId should has format 'groupId:artifactId')");
    }

    return Pair.create(mavenId.substring(0, idx), mavenId.substring(idx + 1));
  }

  public static Map<String, Map<String, Map<String, List<MavenPluginDescriptor>>>> getDescriptorsMap() {
    Map<String, Map<String, Map<String, List<MavenPluginDescriptor>>>> res = ourDescriptorsMap;
    if (res == null) {
      res = new HashMap<>();

      for (MavenPluginDescriptor pluginDescriptor : MavenPluginDescriptor.EP_NAME.getExtensions()) {
        Pair<String, String> pluginId = parsePluginId(pluginDescriptor.mavenId);

        Map<String, Map<String, List<MavenPluginDescriptor>>> groupMap = MavenUtil.getOrCreate(res, pluginId.second);// pluginId.second is artifactId

        Map<String, List<MavenPluginDescriptor>> goalsMap = MavenUtil.getOrCreate(groupMap, pluginId.first);// pluginId.first is groupId

        List<MavenPluginDescriptor> descriptorList = goalsMap.get(pluginDescriptor.goal);
        if (descriptorList == null) {
          descriptorList = new SmartList<>();
          goalsMap.put(pluginDescriptor.goal, descriptorList);
        }

        descriptorList.add(pluginDescriptor);
      }

      ourDescriptorsMap = res;
    }

    return res;
  }

  public static boolean processDescriptors(Processor<MavenPluginDescriptor> processor, MavenDomConfiguration cfg) {
    Map<String, Map<String, Map<String, List<MavenPluginDescriptor>>>> map = getDescriptorsMap();

    DomElement parent = cfg.getParent();

    MavenDomPlugin plugin = DomUtil.getParentOfType(parent, MavenDomPlugin.class, false);
    if (plugin == null) return true;

    Map<String, Map<String, List<MavenPluginDescriptor>>> groupMap = map.get(plugin.getArtifactId().getStringValue());
    if (groupMap == null) return true;

    Map<String, List<MavenPluginDescriptor>> goalsMap = groupMap.get(plugin.getGroupId().getStringValue());
    if (goalsMap == null) return true;

    List<MavenPluginDescriptor> descriptorsForAllGoals = goalsMap.get(null);
    if (descriptorsForAllGoals != null) {
      for (MavenPluginDescriptor descriptor : descriptorsForAllGoals) {
        if (!processor.process(descriptor)) return false;
      }
    }

    if (parent instanceof MavenDomPluginExecution) {
      for (MavenDomGoal goal : ((MavenDomPluginExecution)parent).getGoals().getGoals()) {
        List<MavenPluginDescriptor> descriptors = goalsMap.get(goal.getStringValue());
        if (descriptors != null) {
          for (MavenPluginDescriptor descriptor : descriptors) {
            if (!processor.process(descriptor)) return false;
          }
        }
      }
    }

    return true;
  }
}

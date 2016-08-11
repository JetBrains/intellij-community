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

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.Required;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.reflect.DomExtender;
import com.intellij.util.xml.reflect.DomExtension;
import com.intellij.util.xml.reflect.DomExtensionsRegistrar;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.converters.MavenDomConvertersRegistry;
import org.jetbrains.idea.maven.dom.converters.MavenPluginCustomParameterValueConverter;
import org.jetbrains.idea.maven.dom.model.MavenDomConfiguration;
import org.jetbrains.idea.maven.dom.model.MavenDomConfigurationParameter;
import org.jetbrains.idea.maven.dom.model.MavenDomPluginExecution;
import org.jetbrains.idea.maven.dom.plugin.MavenDomMojo;
import org.jetbrains.idea.maven.dom.plugin.MavenDomParameter;
import org.jetbrains.idea.maven.dom.plugin.MavenDomPluginModel;

import java.lang.annotation.Annotation;
import java.util.*;

public class MavenPluginConfigurationDomExtender extends DomExtender<MavenDomConfiguration> {
  public static final Key<ParameterData> PLUGIN_PARAMETER_KEY = Key.create("MavenPluginConfigurationDomExtender.PLUGIN_PARAMETER_KEY");

  private static final Set<String> COLLECTIONS_TYPE_NAMES = ContainerUtil.immutableSet("java.util.Collection", CommonClassNames.JAVA_UTIL_SET,
                                                                          CommonClassNames.JAVA_UTIL_LIST,
                                                                          "java.util.ArrayList", "java.util.HashSet",
                                                                          "java.util.LinkedList");

  @Override
  public void registerExtensions(@NotNull MavenDomConfiguration config, @NotNull DomExtensionsRegistrar r) {
    MavenDomPluginModel pluginModel = MavenPluginDomUtil.getMavenPluginModel(config);
    if (pluginModel == null) {
      r.registerCustomChildrenExtension(MavenDomConfigurationParameter.class);
      return;
    }

    boolean isInPluginManagement = isInPluginManagement(config);

    for (ParameterData each : collectParameters(pluginModel, config)) {
      registerPluginParameter(isInPluginManagement, r, each);
    }
  }

  private static boolean isInPluginManagement(MavenDomConfiguration pluginNode) {
    XmlElement xmlElement = pluginNode.getXmlElement();
    if (xmlElement == null) return false;

    PsiElement pluginTag = xmlElement.getParent();
    if (pluginTag == null) return false;

    PsiElement pluginsTag = pluginTag.getParent();
    if (pluginsTag == null) return false;

    PsiElement pluginManagementTag = pluginsTag.getParent();

    return pluginManagementTag instanceof XmlTag && "pluginManagement".equals(((XmlTag)pluginManagementTag).getName());
  }

  private static Collection<ParameterData> collectParameters(MavenDomPluginModel pluginModel, MavenDomConfiguration config) {
    List<String> selectedGoals = null;

    MavenDomPluginExecution executionElement = config.getParentOfType(MavenDomPluginExecution.class, false);
    if (executionElement != null) {
      selectedGoals = new ArrayList<>();

      String id = executionElement.getId().getStringValue();
      String defaultPrefix = "default-";
      if (id != null && id.startsWith(defaultPrefix)) {
        String goal = id.substring(defaultPrefix.length());
        if (!StringUtil.isEmptyOrSpaces(goal)) selectedGoals.add(goal);
      }

      for (GenericDomValue<String> goal : executionElement.getGoals().getGoals()) {
        selectedGoals.add(goal.getStringValue());
      }
    }

    Map<String, ParameterData> namesWithParameters = new THashMap<>();

    for (MavenDomMojo eachMojo : pluginModel.getMojos().getMojos()) {
      String goal = eachMojo.getGoal().getStringValue();
      if (goal == null) continue;

      if (selectedGoals == null || selectedGoals.contains(goal)) {
        for (MavenDomParameter eachParameter : eachMojo.getParameters().getParameters()) {
          if (eachParameter.getEditable().getValue() == Boolean.FALSE) continue;

          String name = eachParameter.getName().getStringValue();
          if (name == null) continue;

          ParameterData data = new ParameterData(eachParameter);
          fillParameterData(name, data, eachMojo);

          ParameterData oldParameter = namesWithParameters.get(name);
          if (oldParameter == null || hasMorePriority(data, oldParameter, executionElement != null)) {
            namesWithParameters.put(name, data);
          }
        }
      }
    }

    return namesWithParameters.values();
  }

  private static boolean hasMorePriority(ParameterData d1, ParameterData d2, boolean isForExecutionSection) {
    if (!isForExecutionSection) {
      if (StringUtil.isEmptyOrSpaces(d1.getMojo().getPhase().getStringValue())) return false;

      if (StringUtil.isEmptyOrSpaces(d2.getMojo().getPhase().getStringValue())) return true;
    }

    return d1.getRequiringLevel() > d2.getRequiringLevel();
  }
  
  private static void fillParameterData(String name, ParameterData data, MavenDomMojo mojo) {
    XmlTag config = mojo.getConfiguration().getXmlTag();
    if (config == null) return;

    for (XmlTag each : config.getSubTags()) {
      if (!name.equals(each.getName())) continue;
      data.defaultValue = each.getAttributeValue("default-value");
      data.expression = each.getValue().getTrimmedText();
    }
  }

  private static void registerPluginParameter(boolean isInPluginManagement, DomExtensionsRegistrar r, final ParameterData parameter) {
    String paramName = parameter.parameter.getName().getStringValue();
    String alias = parameter.parameter.getAlias().getStringValue();

    registerPluginParameter(isInPluginManagement, r, parameter, paramName);
    if (alias != null) registerPluginParameter(isInPluginManagement, r, parameter, alias);
  }

  private static void registerPluginParameter(boolean isInPluginManagement, DomExtensionsRegistrar r, final ParameterData data, final String parameterName) {
    DomExtension e = r.registerFixedNumberChildExtension(new XmlName(parameterName), MavenDomConfigurationParameter.class);

    if (isCollection(data.parameter)) {
      e.addExtender(new DomExtender() {
        public void registerExtensions(@NotNull DomElement domElement, @NotNull DomExtensionsRegistrar registrar) {
          for (String each : collectPossibleNameForCollectionParameter(parameterName)) {
            DomExtension inner = registrar.registerCollectionChildrenExtension(new XmlName(each), MavenDomConfigurationParameter.class);
            inner.setDeclaringElement(data.parameter);
          }
        }
      });
    }
    else {
      addValueConverter(e, data.parameter);
      if (!isInPluginManagement) {
        addRequiredAnnotation(e, data);
      }
    }

    e.setDeclaringElement(data.parameter);

    data.parameter.getXmlElement().putUserData(PLUGIN_PARAMETER_KEY, data);
  }

  private static void addValueConverter(DomExtension e, MavenDomParameter parameter) {
    String type = parameter.getType().getStringValue();
    if (!StringUtil.isEmptyOrSpaces(type)) {
      e.setConverter(new MavenPluginCustomParameterValueConverter(type), MavenDomConvertersRegistry.getInstance().isSoft(type));
    }
  }

  private static void addRequiredAnnotation(DomExtension e, final ParameterData data) {
    if (Boolean.parseBoolean(data.parameter.getRequired().getStringValue())
        && StringUtil.isEmptyOrSpaces(data.defaultValue)
        && StringUtil.isEmptyOrSpaces(data.expression)) {
      e.addCustomAnnotation(new Required(){
        @Override
        public boolean value() {
          return true;
        }

        @Override
        public boolean nonEmpty() {
          return false;
        }

        @Override
        public boolean identifier() {
          return false;
        }
        public Class<? extends Annotation> annotationType() {
              return Required.class;
        }
      });
    }
  }

  public static List<String> collectPossibleNameForCollectionParameter(String parameterName) {
    String singularName = StringUtil.unpluralize(parameterName);
    if (singularName == null) singularName = parameterName;

    List<String> result = new ArrayList<>();
    String[] parts = NameUtil.splitNameIntoWords(singularName);
    for (int i = 0; i < parts.length; i++) {
      result.add(StringUtil.decapitalize(StringUtil.join(parts, i, parts.length, "")));
    }
    return result;
  }

  private static boolean isCollection(MavenDomParameter parameter) {
    String type = parameter.getType().getStringValue();
    if (type == null) return false;

    return type.endsWith("[]") || COLLECTIONS_TYPE_NAMES.contains(type);
  }

  public static class ParameterData {
    public final MavenDomParameter parameter;
    public @Nullable String defaultValue;
    public @Nullable String expression;

    private ParameterData(MavenDomParameter parameter) {
      this.parameter = parameter;
    }
    
    @NotNull
    public MavenDomMojo getMojo() {
      return (MavenDomMojo)parameter.getParent().getParent();
    }

    public int getRequiringLevel() {
      if (!Boolean.parseBoolean(parameter.getRequired().getStringValue())) return 0;

      if (!StringUtil.isEmptyOrSpaces(defaultValue) || !StringUtil.isEmptyOrSpaces(expression)) {
        return 1;
      }

      return 2;
    }
  }

}

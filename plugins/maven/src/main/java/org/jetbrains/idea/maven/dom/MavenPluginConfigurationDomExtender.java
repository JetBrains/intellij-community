package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.reflect.DomExtender;
import com.intellij.util.xml.reflect.DomExtension;
import com.intellij.util.xml.reflect.DomExtensionsRegistrar;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.model.MavenDomConfiguration;
import org.jetbrains.idea.maven.dom.model.MavenDomConfigurationParameter;
import org.jetbrains.idea.maven.dom.model.MavenDomPluginExecution;
import org.jetbrains.idea.maven.dom.plugin.MavenDomMojo;
import org.jetbrains.idea.maven.dom.plugin.MavenDomParameter;
import org.jetbrains.idea.maven.dom.plugin.MavenDomPluginModel;

import java.util.*;

public class MavenPluginConfigurationDomExtender extends DomExtender<MavenDomConfiguration> {
  public static final Key<MavenDomParameter> PLUGIN_PARAMETER_KEY = Key.create("MavenPluginConfigurationDomExtender.PLUGIN_PARAMETER_KEY");

  @Override
  public void registerExtensions(@NotNull MavenDomConfiguration config, @NotNull DomExtensionsRegistrar r) {
    MavenDomPluginModel pluginModel = MavenPluginDomUtil.getMavenPluginModel(config);
    if (pluginModel == null) {
      r.registerCustomChildrenExtension(MavenDomConfigurationParameter.class);
      return;
    }

    for (MavenDomParameter each : collectParameters(pluginModel, config)) {
      registerPluginParameter(r, each);
    }
  }

  private Collection<MavenDomParameter> collectParameters(MavenDomPluginModel pluginModel, MavenDomConfiguration config) {
    List<String> selectedGoals = null;

    MavenDomPluginExecution executionElement = config.getParentOfType(MavenDomPluginExecution.class, false);
    if (executionElement != null) {
      selectedGoals = new ArrayList<String>();
      for (GenericDomValue<String> goal : executionElement.getGoals().getGoals()) {
        selectedGoals.add(goal.getStringValue());
      }
    }

    Map<String, MavenDomParameter> namesWithParameters = new THashMap<String, MavenDomParameter>();

    for (MavenDomMojo eachMojo : pluginModel.getMojos().getMojos()) {
      String goal = eachMojo.getGoal().getStringValue();
      if (goal == null) continue;

      if (selectedGoals == null || selectedGoals.contains(goal)) {
        for (MavenDomParameter eachParameter : eachMojo.getParameters().getParameters()) {
          if (!eachParameter.getEditable().getValue()) continue;

          String name = eachParameter.getName().getStringValue();
          if (name == null) continue;

          if (namesWithParameters.containsKey(name)) continue;
          namesWithParameters.put(name, eachParameter);
        }
      }
    }

    return namesWithParameters.values();
  }

  private void registerPluginParameter(DomExtensionsRegistrar r, final MavenDomParameter parameter) {
    String paramName = parameter.getName().getStringValue();
    String alias = parameter.getAlias().getStringValue();

    registerPluginParameter(r, parameter, paramName);
    if (alias != null) registerPluginParameter(r, parameter, alias);
  }

  private void registerPluginParameter(DomExtensionsRegistrar r, final MavenDomParameter parameter, final String parameterName) {
    DomExtension e;
    if (isCollection(parameter)) {
      e = r.registerFixedNumberChildExtension(new XmlName(parameterName), MavenDomConfigurationParameter.class);
      e.addExtender(new DomExtender() {
        public void registerExtensions(@NotNull DomElement domElement, @NotNull DomExtensionsRegistrar registrar) {
          for (String each : collectPossibleNameForCollectionParameter(parameterName)) {
            DomExtension inner = registrar.registerCollectionChildrenExtension(new XmlName(each), MavenDomConfigurationParameter.class);
            inner.putUserData(DomExtension.KEY_DECLARATION, parameter);
          }
        }
      });
    }
    else {
      e = r.registerFixedNumberChildExtension(new XmlName(parameterName), MavenDomConfigurationParameter.class);
    }
    e.putUserData(DomExtension.KEY_DECLARATION, parameter);

    parameter.getXmlElement().putUserData(PLUGIN_PARAMETER_KEY, parameter);
  }

  public List<String> collectPossibleNameForCollectionParameter(String parameterName) {
    String singularName = StringUtil.unpluralize(parameterName);
    if (singularName == null) singularName = parameterName;

    List<String> result = new ArrayList<String>();
    String[] parts = NameUtil.splitNameIntoWords(singularName);
    for (int i = 0; i < parts.length; i++) {
      result.add(StringUtil.decapitalize(StringUtil.join(parts, i, parts.length, "")));
    }
    return result;
  }

  private boolean isCollection(MavenDomParameter parameter) {
    String type = parameter.getType().getStringValue();
    if (type.endsWith("[]")) return true;

    List<String> collectionClasses = Arrays.asList("java.util.List",
                                                   "java.util.Set",
                                                   "java.util.Collection");
    return collectionClasses.contains(type);
  }
}

package com.intellij.structuralsearch.plugin.ui;

import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.structuralsearch.MatchOptions;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Apr 14, 2004
 * Time: 5:29:37 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class Configuration implements JDOMExternalizable {
  @NonNls protected static final String NAME_ATTRIBUTE_NAME = "name";
  private String name = "";
  private boolean predefined;

  private static ConfigurationCreator configurationCreator;

  public String getName() {
    return name;
  }

  public void setName(String value) {
    name = value;
  }

  public void readExternal(Element element) {
    name = element.getAttributeValue(NAME_ATTRIBUTE_NAME);
  }

  public void writeExternal(Element element) {
    element.setAttribute(NAME_ATTRIBUTE_NAME,name);
  }

  public boolean isPredefined() {
    return predefined;
  }

  public void setPredefined(boolean predefined) {
    this.predefined = predefined;
  }

  public abstract MatchOptions getMatchOptions();

  public boolean equals(Object configuration) {
    if (!(configuration instanceof Configuration)) return false;
    Configuration other = (Configuration)configuration;
    if (!getMatchOptions().equals(other.getMatchOptions())) return false;
    if (!getName().equals(other.getName())) return false;
    return true;
  }

  public int hashCode() {
    return getMatchOptions().hashCode();
  }

  public static void setActiveCreator(ConfigurationCreator creator) {
    configurationCreator = creator;
  }

  public static ConfigurationCreator getConfigurationCreator() {
    return configurationCreator;
  }

  @NonNls public static final String CONTEXT_VAR_NAME = "__context__";
}

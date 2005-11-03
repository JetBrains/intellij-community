package com.intellij.structuralsearch.plugin.ui;

import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 10.02.2004
 * Time: 14:29:45
 * To change this template use File | Settings | File Templates.
 */
public class ConfigurationManager {
  @NonNls private static final String SAVE_TAG_NAME = "searchConfiguration";
  @NonNls private static final String SAVE_TAG_NAME2 = "replaceConfiguration";
  @NonNls private static final String SAVE_HISTORY_ATTR_NAME = "history";

  private List<Configuration> configurations;
  private LinkedList<Configuration> historyConfigurations;

  public void addHistoryConfiguration(Configuration configuration) {
    if (historyConfigurations == null) historyConfigurations = new LinkedList<Configuration>();

    if (historyConfigurations.indexOf(configuration)==-1) {
      historyConfigurations.addFirst(configuration);
    }
  }

  public void removeHistoryConfiguration(Configuration configuration) {
    if (historyConfigurations!=null) {
      historyConfigurations.remove(configuration);
    }
  }

  public void addConfiguration(Configuration configuration) {
    if (configurations == null) configurations = new LinkedList<Configuration>();

    if (configurations.indexOf(configuration)==-1) {
      configurations.add( configuration );
    }
  }

  public void removeConfiguration(Configuration configuration) {
    if (configurations!=null) {
      configurations.remove(configuration);
    }
  }

  public void saveConfigurations(Element element) {
    if (configurations!=null) {
      for (final Configuration configuration : configurations) {
        saveConfiguration(element, configuration);
      }
    }

    if (historyConfigurations!=null) {
      for (final Configuration historyConfiguration : historyConfigurations) {
        final Element infoElement = saveConfiguration(element, historyConfiguration);
        infoElement.setAttribute(SAVE_HISTORY_ATTR_NAME, "1");
      }
    }
  }

  private static Element saveConfiguration(Element element, final Configuration config) {
    Element infoElement = new Element(config instanceof SearchConfiguration ? SAVE_TAG_NAME : SAVE_TAG_NAME2);
    element.addContent(infoElement);
    config.writeExternal(infoElement);

    return infoElement;
  }

  public void loadConfigurations(Element element) {
    if (configurations != null) return;
    List patterns = element.getChildren();

    if (patterns!=null && patterns.size() > 0) {
      for (final Object pattern : patterns) {
        final Element childElement = (Element)pattern;
        final Configuration config =
          childElement.getName().equals(SAVE_TAG_NAME) ? new SearchConfiguration() : new ReplaceConfiguration();

        config.readExternal(childElement);

        if (childElement.getAttribute(SAVE_HISTORY_ATTR_NAME) != null) {
          if (historyConfigurations == null) historyConfigurations = new LinkedList<Configuration>();
          historyConfigurations.add(config);
        }
        else {
          addConfiguration(config);
        }
      }
    }
  }

  public Collection<Configuration> getConfigurations() {
    return configurations;
  }

  public Collection<Configuration> getHistoryConfigurations() {
    return historyConfigurations;
  }
}

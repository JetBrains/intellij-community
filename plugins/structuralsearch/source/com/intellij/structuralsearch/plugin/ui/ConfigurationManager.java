package com.intellij.structuralsearch.plugin.ui;

import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 10.02.2004
 * Time: 14:29:45
 * To change this template use File | Settings | File Templates.
 */
public class ConfigurationManager {
  @NonNls private static final String SEARCH_TAG_NAME = "searchConfiguration";
  @NonNls private static final String REPLACE_TAG_NAME = "replaceConfiguration";
  @NonNls private static final String SAVE_HISTORY_ATTR_NAME = "history";

  private List<Configuration> configurations;
  private LinkedList<Configuration> historyConfigurations;

  public void addHistoryConfigurationToFront(Configuration configuration) {
    if (historyConfigurations == null) historyConfigurations = new LinkedList<Configuration>();

    if (historyConfigurations.indexOf(configuration) == -1) {
      historyConfigurations.addFirst(configuration);
    }
  }

  public void removeHistoryConfiguration(Configuration configuration) {
    if (historyConfigurations != null) {
      historyConfigurations.remove(configuration);
    }
  }

  public void addConfiguration(Configuration configuration) {
    if (configurations == null) configurations = new LinkedList<Configuration>();

    if (configurations.indexOf(configuration) == -1) {
      configurations.add(configuration);
    }
  }

  public void removeConfiguration(Configuration configuration) {
    if (configurations != null) {
      configurations.remove(configuration);
    }
  }

  public void saveConfigurations(Element element) {
    writeConfigurations(element, configurations, historyConfigurations);
  }

  public static void writeConfigurations(final Element element,
                                   final Collection<Configuration> configurations,
                                   final Collection<Configuration> historyConfigurations) {
    if (configurations != null) {
      for (final Configuration configuration : configurations) {
        saveConfiguration(element, configuration);
      }
    }

    if (historyConfigurations != null) {
      for (final Configuration historyConfiguration : historyConfigurations) {
        final Element infoElement = saveConfiguration(element, historyConfiguration);
        infoElement.setAttribute(SAVE_HISTORY_ATTR_NAME, "1");
      }
    }
  }

  public static Element saveConfiguration(Element element, final Configuration config) {
    Element infoElement = new Element(config instanceof SearchConfiguration ? SEARCH_TAG_NAME : REPLACE_TAG_NAME);
    element.addContent(infoElement);
    config.writeExternal(infoElement);

    return infoElement;
  }

  public void loadConfigurations(Element element) {
    if (configurations != null) return;
    ArrayList<Configuration> configurations = new ArrayList<Configuration>();
    ArrayList<Configuration> historyConfigurations = new ArrayList<Configuration>();
    readConfigurations(element, configurations, historyConfigurations);
    for (Configuration configuration : historyConfigurations) {
      addHistoryConfigurationToFront(configuration);
    }
    for (Configuration configuration : configurations) {
      addConfiguration(configuration);
    }
    if (this.historyConfigurations != null) {
      Collections.reverse(this.historyConfigurations);
    }
  }

  public static void readConfigurations(final Element element, @NotNull Collection<Configuration> configurations, @NotNull Collection<Configuration> historyConfigurations) {
    List patterns = element.getChildren();

    if (patterns != null && patterns.size() > 0) {
      for (final Object pattern : patterns) {
        final Element childElement = (Element)pattern;
        final Configuration config = readConfiguration(childElement);

        if (childElement.getAttribute(SAVE_HISTORY_ATTR_NAME) != null) {
          historyConfigurations.add(config);
        }
        else {
          configurations.add(config);
        }
      }
    }
  }

  public static Configuration readConfiguration(final Element childElement) {
    final Configuration config =
      childElement.getName().equals(SEARCH_TAG_NAME) ? new SearchConfiguration() : new ReplaceConfiguration();

    config.readExternal(childElement);
    return config;
  }

  public Collection<Configuration> getConfigurations() {
    return configurations;
  }

  public Collection<Configuration> getHistoryConfigurations() {
    return historyConfigurations;
  }
}

package com.intellij.structuralsearch.plugin.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NonEmptyInputValidator;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 10.02.2004
 * Time: 14:29:45
 * To change this template use File | Settings | File Templates.
 */
public class ConfigurationManager {
  @NonNls static final String SEARCH_TAG_NAME = "searchConfiguration";
  @NonNls static final String REPLACE_TAG_NAME = "replaceConfiguration";
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
    if (configurations == null) configurations = new ArrayList<Configuration>();

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
    final List<Element> patterns = element.getChildren();

    if (patterns != null && patterns.size() > 0) {
      for (final Element pattern : patterns) {
        final Configuration config = readConfiguration(pattern);
        if (config == null) continue;

        if (pattern.getAttribute(SAVE_HISTORY_ATTR_NAME) != null) {
          historyConfigurations.add(config);
        }
        else {
          configurations.add(config);
        }
      }
    }
  }

  public static Configuration readConfiguration(final Element childElement) {
    String s = childElement.getName();
    final Configuration config =
      s.equals(SEARCH_TAG_NAME) ? new SearchConfiguration() : s.equals(REPLACE_TAG_NAME) ? new ReplaceConfiguration():null;
    if (config != null) config.readExternal(childElement);
    return config;
  }

  public Collection<Configuration> getConfigurations() {
    return configurations;
  }

  public static Configuration findConfigurationByName(final Collection<Configuration> configurations, final String name) {
    for(Configuration config:configurations) {
      if (config.getName().equals(name)) return config;
    }

    return null;
  }

  public Collection<Configuration> getHistoryConfigurations() {
    return historyConfigurations;
  }

  public static @Nullable String findAppropriateName(@NotNull final Collection<Configuration> configurations, @NotNull String _name,
                                                     @NotNull final Project project) {
    Configuration config;
    String name = _name;

    while ((config = findConfigurationByName(configurations, name)) != null) {
      int i = Messages.showYesNoDialog(
        project,
        SSRBundle.message("overwrite.message"),
        SSRBundle.message("overwrite.title"),
        AllIcons.General.QuestionDialog
      );

      if (i == Messages.YES) {
        configurations.remove(config);
        break;
      }
      name = showSaveTemplateAsDialog(name, project);
      if (name == null) break;
    }
    return name;
  }

  public static @Nullable String showSaveTemplateAsDialog(@NotNull String initial, @NotNull Project project) {
    return Messages.showInputDialog(
      project,
      SSRBundle.message("template.name.button"),
      SSRBundle.message("save.template.description.button"),
      AllIcons.General.QuestionDialog,
      initial,
      new NonEmptyInputValidator()
    );
  }
}

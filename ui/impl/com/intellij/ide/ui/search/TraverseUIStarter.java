/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.ui.search;

import com.intellij.application.options.CodeStyleSchemesConfigurable;
import com.intellij.application.options.colors.ColorAndFontOptions;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.codeInsight.intention.impl.config.IntentionSettingsConfigurable;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.keymap.impl.ui.KeymapConfigurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.profile.ui.InspectionProfileConfigurable;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;
import java.util.*;

/**
 * User: anna
 * Date: 13-Feb-2006
 */
@SuppressWarnings({"CallToPrintStackTrace", "SynchronizeOnThis"})
public class TraverseUIStarter implements ApplicationStarter {
  private String OUTPUT_PATH;
  @NonNls private static final String OPTIONS = "options";
  @NonNls private static final String CONFIGURABLE = "configurable";
  @NonNls private static final String ID = "id";
  @NonNls private static final String CONFIGURABLE_NAME = "configurable_name";
  @NonNls private static final String OPTION = "option";
  @NonNls private static final String NAME = "name";
  @NonNls private static final String PATH = "path";

  @NonNls
  public String getCommandName() {
    return "traverseUI";
  }


  public void premain(String[] args) {
    OUTPUT_PATH = args[1];
  }

  public void main(String[] args){
    try {
      startup();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void startup() throws IOException {
    final HashMap<SearchableConfigurable, Set<Pair<String, String>>> options =
      new HashMap<SearchableConfigurable, Set<Pair<String, String>>>();
    SearchUtil.processProjectConfigurables(ProjectManager.getInstance().getDefaultProject(), options);
    Element root = new Element(OPTIONS);
    for (SearchableConfigurable configurable : options.keySet()) {
      Element configurableElement = new Element(CONFIGURABLE);
      final String id = configurable.getId();
      if (id == null) continue;
      configurableElement.setAttribute(ID, id);
      configurableElement.setAttribute(CONFIGURABLE_NAME, configurable.getDisplayName());
      TreeSet<Pair<String, String>> sortedOptions = new TreeSet<Pair<String, String>>(new Comparator<Pair<String, String>>() {
        public int compare(final Pair<String, String> o1, final Pair<String, String> o2) {
          return o1.first.compareTo(o2.first);
        }
      });
      final Set<Pair<String,String>> strings = options.get(configurable);
      for (Pair<String,String> option : strings) {
        if (option == null || option.first == null || option.first.length() == 0) continue;
        sortedOptions.add(Pair.create(option.first, option.second));
      }
      for (Pair<String,String> option : sortedOptions) {
        Element optionElement = new Element(OPTION);
        optionElement.setAttribute(NAME, option.first);
        if (option.second != null) {
          optionElement.setAttribute(PATH, option.second);
        }
        configurableElement.addContent(optionElement);
      }
      if (configurable instanceof KeymapConfigurable){
        processKeymap(configurableElement);
      } else if (configurable instanceof InspectionProfileConfigurable){
        processInspectionTools(configurableElement);
      } else if (configurable instanceof IntentionSettingsConfigurable){
        processIntentions(configurableElement);
      } else if (configurable instanceof ColorAndFontOptions){
        processColorAndFontsSettings((ColorAndFontOptions)configurable, configurableElement);
      } else if (configurable instanceof CodeStyleSchemesConfigurable){
        processCodeStyleConfigurable((CodeStyleSchemesConfigurable)configurable, configurableElement);
      }
      root.addContent(configurableElement);
    }
    JDOMUtil.writeDocument(new Document(root), OUTPUT_PATH, "\n");

    ((ApplicationEx)ApplicationManager.getApplication()).exit(true);
  }

  private static void processCodeStyleConfigurable(final CodeStyleSchemesConfigurable configurable, final Element configurableElement) {
    final TreeSet<Pair<String, String>> options = new TreeSet<Pair<String, String>>(new Comparator<Pair<String, String>>() {
      public int compare(final Pair<String, String> o1, final Pair<String, String> o2) {
        return o1.first.compareTo(o2.first);
      }
    });
    options.addAll(configurable.processOptions());
    for (Pair<String, String> pair : options) {
      Element optionElement = new Element(OPTION);
      optionElement.setAttribute(NAME, pair.first);
      if (pair.second != null) {
        optionElement.setAttribute(PATH, pair.second);
      }
      configurableElement.addContent(optionElement);
    }
  }

  private static void processColorAndFontsSettings(final ColorAndFontOptions configurable, final Element configurableElement) {
    final Map<String, String> optionsPath = configurable.processListOptions();
    final Map<String, String> result = new TreeMap<String, String>();
    for (String opt : optionsPath.keySet()) {
      final String path = optionsPath.get(opt);
      final Set<String> words = SearchUtil.getProcessedWords(opt);
      for (String word : words) {
        if (word != null){
          result.put(word, path);
        }
      }
    }
    for (String option : result.keySet()) {
      final String path = result.get(option);
      Element optionElement = new Element(OPTION);
      optionElement.setAttribute(NAME, option);
      optionElement.setAttribute(PATH, path);
      configurableElement.addContent(optionElement);
    }
  }

  private static void processKeymap(final Element configurableElement){
    final ActionManager actionManager = ActionManager.getInstance();
    final Set<String> ids = ((ActionManagerImpl)actionManager).getActionIds();
    final TreeSet<String> options = new TreeSet<String>();
    for (String id : ids) {
      final AnAction anAction = actionManager.getAction(id);
      final String text = anAction.getTemplatePresentation().getText();
      if (text != null) {
        options.addAll(SearchUtil.getProcessedWords(text));
      }
      final String description = anAction.getTemplatePresentation().getDescription();
      if (description != null) {
        options.addAll(SearchUtil.getProcessedWords(description));
      }
    }
    for (String opt : options) {
      Element optionElement = new Element(OPTION);
      optionElement.setAttribute(NAME, opt);
      configurableElement.addContent(optionElement);
    }
  }

  private static void processIntentions(final Element configurableElement) {
    final IntentionManagerSettings intentionManagerSettings = IntentionManagerSettings.getInstance();
    intentionManagerSettings.buildIndex();
    final TreeSet<String> words = new TreeSet<String>(intentionManagerSettings.getIntentionWords());
    for (String word : words) {
      final List<String> mentionedIntentions = intentionManagerSettings.getFilteredIntentionNames(word);
      appendToolsToPath(mentionedIntentions, word, configurableElement);
    }
  }

  private void processInspectionTools(final Element configurableElement) {
    InspectionToolRegistrar.getInstance().createTools(); //force index building
    while (!InspectionToolRegistrar.isIndexBuild()){//wait for index build
      synchronized (this) {
        try {
          wait(100);
        }
        catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }
    final TreeSet<String> words = new TreeSet<String>(InspectionToolRegistrar.getToolWords());
    for (String word : words) {
      final List<String> mentionedInspections = InspectionToolRegistrar.getFilteredToolNames(word);
      appendToolsToPath(mentionedInspections, word, configurableElement);
    }
  }

  private static void appendToolsToPath(final List<String> mentionedInspections, final String word, final Element configurableElement) {
    StringBuffer allInspections = new StringBuffer();
    for (String inspection : mentionedInspections) {
      allInspections.append(allInspections.length() > 0 ? ";" : "").append(inspection);
    }
    Element optionElement = new Element(OPTION);
    optionElement.setAttribute(NAME, word);
    optionElement.setAttribute(PATH, allInspections.toString());
    configurableElement.addContent(optionElement);
  }
}

/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.ui.search;

import com.intellij.application.options.colors.ColorAndFontOptions;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.codeInsight.intention.impl.config.IntentionSettingsConfigurable;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.openapi.application.ApplicationStarter;
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
@SuppressWarnings({"CallToPrintStackTrace", "HardCodedStringLiteral"})
public class TraverseUIStarter implements ApplicationStarter {
  private String OUTPUT_PATH;

  @NonNls
  public String getCommandName() {
    return "traverseUI";
  }


  public void premain(String[] args) {
    System.setProperty("idea.load.plugins.category", "traverseUI");
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
    Element root = new Element("options");
    for (SearchableConfigurable configurable : options.keySet()) {
      Element configurableElement = new Element("configurable");
      final String id = configurable.getId();
      if (id == null) continue;
      configurableElement.setAttribute("id", id);
      configurableElement.setAttribute("configurable_name", configurable.getDisplayName());
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
        Element optionElement = new Element("option");
        optionElement.setAttribute("name", option.first);
        if (option.second != null) {
          optionElement.setAttribute("path", option.second);
        }
        configurableElement.addContent(optionElement);
      }
      if (configurable instanceof InspectionProfileConfigurable){
        processInspectionTools(configurableElement);
      } else if (configurable instanceof IntentionSettingsConfigurable){
        processIntentions(configurableElement);
      } else if (configurable instanceof ColorAndFontOptions){
        processColorAndFontsSettings((ColorAndFontOptions)configurable, configurableElement);
      }
      root.addContent(configurableElement);
    }
    JDOMUtil.writeDocument(new Document(root), OUTPUT_PATH, "\n");
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
      Element optionElement = new Element("option");
      optionElement.setAttribute("name", option);
      optionElement.setAttribute("path", path);
      configurableElement.addContent(optionElement);
    }
  }

  private void processIntentions(final Element configurableElement) {
    final IntentionManagerSettings intentionManagerSettings = IntentionManagerSettings.getInstance();
    intentionManagerSettings.buildIndex();
     while (!intentionManagerSettings.isIndexBuild()){//wait for index build
      synchronized (this) {
        try {
          wait(100);
        }
        catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }
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
    Element optionElement = new Element("option");
    optionElement.setAttribute("name", word);
    optionElement.setAttribute("path", allInspections.toString());
    configurableElement.addContent(optionElement);
  }
}

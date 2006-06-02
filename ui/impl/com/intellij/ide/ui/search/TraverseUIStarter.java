/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.ui.search;

import com.intellij.application.options.CodeStyleSchemesConfigurable;
import com.intellij.application.options.colors.ColorAndFontOptions;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.codeInsight.intention.impl.config.IntentionSettingsConfigurable;
import com.intellij.codeInspection.ex.InspectionTool;
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
import com.intellij.profile.ui.ErrorOptionsConfigurable;
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
  @NonNls private static final String HIT = "hit";

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
    final HashMap<SearchableConfigurable, TreeSet<OptionDescription>> options =
      new HashMap<SearchableConfigurable, TreeSet<OptionDescription>>();
    SearchUtil.processProjectConfigurables(ProjectManager.getInstance().getDefaultProject(), options);
    Element root = new Element(OPTIONS);
    for (SearchableConfigurable configurable : options.keySet()) {
      Element configurableElement = new Element(CONFIGURABLE);
      final String id = configurable.getId();
      if (id == null) continue;
      configurableElement.setAttribute(ID, id);
      configurableElement.setAttribute(CONFIGURABLE_NAME, configurable.getDisplayName());
      final TreeSet<OptionDescription> sortedOptions = options.get(configurable);
      for (OptionDescription option : sortedOptions) {
        append(option.getPath(), option.getHit(), option.getOption(), configurableElement);
      }
      if (configurable instanceof KeymapConfigurable){
        processKeymap(configurableElement);
      } else if (configurable instanceof ErrorOptionsConfigurable){
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
    final TreeSet<OptionDescription> options = new TreeSet<OptionDescription>();
    options.addAll(configurable.processOptions());
    for (OptionDescription description : options) {
      append(description.getPath(), description.getHit(), description.getOption(), configurableElement);
    }
  }

  private static void processColorAndFontsSettings(final ColorAndFontOptions configurable, final Element configurableElement) {
    SearchableOptionsRegistrar searchableOptionsRegistrar = SearchableOptionsRegistrar.getInstance();
    final Map<String, String> optionsPath = configurable.processListOptions();
    final TreeSet<OptionDescription> result = new TreeSet<OptionDescription>();
    for (String opt : optionsPath.keySet()) {
      final String path = optionsPath.get(opt);
      final Set<String> words = searchableOptionsRegistrar.getProcessedWordsWithoutStemming(opt);
      for (String word : words) {
        if (word != null){
          result.add(new OptionDescription(word, opt, path));
        }
      }
    }
    for (OptionDescription option : result) {
      append(option.getPath(), option.getHit(), option.getOption(), configurableElement);
    }
  }

  private static void processKeymap(final Element configurableElement){
    final ActionManager actionManager = ActionManager.getInstance();
    final SearchableOptionsRegistrar searchableOptionsRegistrar = SearchableOptionsRegistrar.getInstance();
    final Set<String> ids = ((ActionManagerImpl)actionManager).getActionIds();
    final TreeSet<OptionDescription> options = new TreeSet<OptionDescription>();
    for (String id : ids) {
      final AnAction anAction = actionManager.getAction(id);
      final String text = anAction.getTemplatePresentation().getText();
      if (text != null) {
        final Set<String> strings = searchableOptionsRegistrar.getProcessedWordsWithoutStemming(text);
        for (String word : strings) {
          options.add(new OptionDescription(word, text, null));
        }
      }
      final String description = anAction.getTemplatePresentation().getDescription();
      if (description != null) {
        final Set<String> strings = searchableOptionsRegistrar.getProcessedWordsWithoutStemming(description);
        for (String word : strings) {
          options.add(new OptionDescription(word, description, null));
        }
      }
    }
    for (OptionDescription opt : options) {
      append(opt.getPath(), opt.getHit(), opt.getOption(), configurableElement);
    }
  }

  private static void processIntentions(final Element configurableElement) {
    final IntentionManagerSettings intentionManagerSettings = IntentionManagerSettings.getInstance();
    intentionManagerSettings.buildIndex();
    final TreeSet<String> words = new TreeSet<String>(intentionManagerSettings.getIntentionWords());
    for (String word : words) {
      final List<String> mentionedIntentions = intentionManagerSettings.getFilteredIntentionNames(word);
      for (String intention : mentionedIntentions) {
        append(intention, intention, word, configurableElement);
      }
    }
  }

  private void processInspectionTools(final Element configurableElement) {
    final InspectionTool[] tools = InspectionToolRegistrar.getInstance().createTools();//force index building
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
      for (InspectionTool tool : tools) {
        if (mentionedInspections.contains(tool.getShortName())){
          append(tool.getShortName(), tool.getDisplayName(), word, configurableElement);
        }
      }
    }
  }

  private static void append(String path, String hit, final String word, final Element configurableElement) {
    Element optionElement = new Element(OPTION);
    optionElement.setAttribute(NAME, word);
    if (path != null) {
      optionElement.setAttribute(PATH, path);
    }
    optionElement.setAttribute(HIT, hit);
    configurableElement.addContent(optionElement);
  }
}

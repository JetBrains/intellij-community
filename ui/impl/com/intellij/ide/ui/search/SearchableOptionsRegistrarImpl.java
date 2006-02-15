/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.ui.search;

import com.intellij.application.options.CodeStyleSchemesConfigurable;
import com.intellij.application.options.ProjectCodeStyleConfigurable;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ResourceUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * User: anna
 * Date: 07-Feb-2006
 */
public class SearchableOptionsRegistrarImpl extends SearchableOptionsRegistrar  implements ApplicationComponent {
  private Map<String, Set<String>> myOption2HelpId = new HashMap<String, Set<String>>();
  private Map<Pair<String, String>, String> myHelpIdWithOption2Path = new HashMap<Pair<String, String>, String>();
  private Set<String> myStopWords = new HashSet<String>();
  private Map<Pair<String,String>, String> myHighlightSynonym2Option = new HashMap<Pair<String, String>, String>();

  @SuppressWarnings({"HardCodedStringLiteral"})
  public SearchableOptionsRegistrarImpl() {
    try {
      //index
      Document document =
        JDOMUtil.loadDocument(ResourceUtil.getResource(SearchableOptionsRegistrar.class, "/search/", "searchableOptions.xml"));
      Element root = document.getRootElement();
      List configurables = root.getChildren("configurable");
      for (final Object o : configurables) {
        final Element configurable = (Element)o;
        final String id = configurable.getAttributeValue("id");
        final List options = configurable.getChildren("option");
        for (Object o1 : options) {
          Element optionElement = (Element)o1;
          final String option = optionElement.getAttributeValue("name");
          final String path = optionElement.getAttributeValue("path");
          putOptionWithHelpId(option, id);
          myHelpIdWithOption2Path.put(Pair.create(id, option), path);
        }
      }

      //synonyms
      document = JDOMUtil.loadDocument(ResourceUtil.getResource(SearchableOptionsRegistrar.class, "/search/", "synonyms.xml"));
      root = document.getRootElement();
      configurables = root.getChildren("configurable");
      for (final Object o : configurables) {
        final Element configurable = (Element)o;
        final String id = configurable.getAttributeValue("id");
        final List synonyms = configurable.getChildren("synonym");
        for (Object o1 : synonyms) {
          Element synonymElement = (Element)o1;
          final String synonym = PorterStemmerUtil.stem(synonymElement.getTextNormalize());
          if (synonym != null) {
            putOptionWithHelpId(synonym, id);
          }
        }
        final List options = configurable.getChildren("option");
        for (Object o1 : options) {
          Element optionElement = (Element)o1;
          final String option = optionElement.getAttributeValue("name");
          final List list = optionElement.getChildren("synonym");
          for (Object o2 : list) {
            Element synonymElement = (Element)o2;
            final String synonym = PorterStemmerUtil.stem(synonymElement.getTextNormalize());
            if (synonym != null) {
              putOptionWithHelpId(synonym, id);
              myHighlightSynonym2Option.put(Pair.create(synonym, id), option);
            }
          }
        }
      }

      //stop words
      final String text = ResourceUtil.loadText(ResourceUtil.getResource(SearchableOptionsRegistrarImpl.class, "/search/", "ignore.txt"));
      final String[] words = text.split("[\\W]");
      myStopWords.addAll(Arrays.asList(words));
    }
    catch (Exception e) {
      //do nothing
    }
  }

  private void putOptionWithHelpId(final String option, final String id) {
    Set<String> helpIds = myOption2HelpId.get(option);
    if (helpIds == null) {
      helpIds = new HashSet<String>();
      myOption2HelpId.put(option, helpIds);
    }
    helpIds.add(id);
  }

  @NotNull
  public Set<Configurable> getConfigurables(ConfigurableGroup[] configurables, String option, boolean showProjectCodeStyle) {
    Set<String> options = SearchUtil.getProcessedWords(option);
    Set<Configurable> result = new HashSet<Configurable>();
    Set<String> helpIds = null;
    for (String opt : options) {
      final Set<String> optionIds = myOption2HelpId.get(opt);
      if (optionIds == null) return result;
      if (helpIds == null){
        helpIds = optionIds;
      }
      helpIds.retainAll(optionIds);
    }
    if (helpIds != null) {
      for (ConfigurableGroup configurableGroup : configurables) {
        final Configurable[] groupConfigurables = configurableGroup.getConfigurables();
        for (Configurable configurable : groupConfigurables) {
          if (configurable instanceof ProjectCodeStyleConfigurable && !showProjectCodeStyle) continue;
          if (configurable instanceof CodeStyleSchemesConfigurable && showProjectCodeStyle) continue;
          if (configurable instanceof SearchableConfigurable && helpIds.contains(((SearchableConfigurable)configurable).getId())){
            result.add(configurable);
          }
        }
      }
    }
    return result;
  }


  public String getInnerPath(SearchableConfigurable configurable, @NonNls String option) {
    final String[] options = option.split("[\\W&&[^_-]]");
    if (options != null && options.length > 0){
      option = PorterStemmerUtil.stem(options[0]);
    }
    return myHelpIdWithOption2Path.get(Pair.create(configurable.getId(), option));
  }

  public boolean isStopWord(String word){
    return myStopWords.contains(word);
  }

  public String getSynonym(final String option, final SearchableConfigurable configurable) {
    return myHighlightSynonym2Option.get(Pair.create(option, configurable.getId()));
  }

  public void addOption(SearchableConfigurable configurable, String option, String path) {
    myHelpIdWithOption2Path.put(Pair.create(configurable.getId(), option), path);
    putOptionWithHelpId(option, configurable.getId());
  }

  @NonNls
  public String getComponentName() {
    return "SearchableOptionsRegistrar";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}

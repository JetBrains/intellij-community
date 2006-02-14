/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.ui.search;

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
public class SearchableOptionsRegistrarImpl extends SearchableOptionsRegistrar {
  private Map<String, Set<String>> myOption2HelpId = new HashMap<String, Set<String>>();
  private Map<Pair<String, String>, String> myHelpIdWithOption2Path = new HashMap<Pair<String, String>, String>();
  private Set<String> myStopWords = new HashSet<String>();


  @SuppressWarnings({"HardCodedStringLiteral"})
  public SearchableOptionsRegistrarImpl() {
    try {
      //index
      final Document document =
        JDOMUtil.loadDocument(ResourceUtil.getResource(SearchableOptionsRegistrarImpl.class, "/search/", "searchableOptions.xml"));
      final Element root = document.getRootElement();
      final List configurables = root.getChildren("configurable");
      for (final Object o : configurables) {
        final Element configurable = (Element)o;
        final String id = configurable.getAttributeValue("id");
        final List options = configurable.getChildren("option");
        for (Object o1 : options) {
          Element optionElement = (Element)o1;
          final String option = optionElement.getAttributeValue("name");
          final String path = optionElement.getAttributeValue("path");
          Set<String> helpIds = myOption2HelpId.get(option);
          if (helpIds == null) {
            helpIds = new HashSet<String>();
            myOption2HelpId.put(option, helpIds);
          }
          helpIds.add(id);

          myHelpIdWithOption2Path.put(Pair.create(id, option), path);
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

  @NotNull
  public Set<Configurable> getConfigurables(ConfigurableGroup[] configurables, String option) {
    Set<Configurable> result = new HashSet<Configurable>();
    if (myStopWords.contains(option)) return result;
    final Set<String> helpIds = myOption2HelpId.get(PorterStemmerUtil.stem(option));
    if (helpIds != null) {
      for (ConfigurableGroup configurable : configurables) {
        final Configurable[] groupConfigurables = configurable.getConfigurables();
        for (Configurable groupConfigurable : groupConfigurables) {
          if (groupConfigurable instanceof SearchableConfigurable && helpIds.contains(((SearchableConfigurable)groupConfigurable).getId())){
            result.add(groupConfigurable);
          }
        }
      }
    }
    return result;
  }


  public String getInnerPath(SearchableConfigurable configurable, String option) {
    option = PorterStemmerUtil.stem(option);
    return myHelpIdWithOption2Path.get(Pair.create(configurable.getId(), option));
  }

  public boolean isStopWord(String word){
    return myStopWords.contains(word);
  }

  public void addOption(SearchableConfigurable configurable, String option, String path) {
    myHelpIdWithOption2Path.put(Pair.create(configurable.getId(), option), path);
    Set<String> helpIds = myOption2HelpId.get(option);
    if (helpIds == null) {
      helpIds = new HashSet<String>();
      myOption2HelpId.put(option, helpIds);
    }
    helpIds.add(configurable.getId());
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

/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.ui.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
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
public class SearchableOptionsRegistrar implements ApplicationComponent {
  private Map<String, Set<String>> myOption2HelpId = new HashMap<String, Set<String>>();
  private Map<Pair<String,String>, String> myHelpIdWithOption2Path = new HashMap<Pair<String, String>, String>();

  public static SearchableOptionsRegistrar getInstance(){
    return ApplicationManager.getApplication().getComponent(SearchableOptionsRegistrar.class);
  }


  @SuppressWarnings({"HardCodedStringLiteral"})
  public SearchableOptionsRegistrar() {
    try {
      final Document document = JDOMUtil.loadDocument(ResourceUtil.getResource(SearchableOptionsRegistrar.class, "/", "searchableOptions.xml"));
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
          if (helpIds == null){
            helpIds = new HashSet<String>();
            myOption2HelpId.put(option, helpIds);
          }
          helpIds.add(id);

          myHelpIdWithOption2Path.put(Pair.create(id, option), path);
        }
      }
    }
    catch (Exception e) {
      //do nothing
    }
   }

  @NotNull
  public Set<Configurable> getConfigurables(ConfigurableGroup[] configurables, String option){
    Set<Configurable> result = new HashSet<Configurable>();
    final String unpluralizedOption = StringUtil.unpluralize(option);
    final Set<String> helpIds = myOption2HelpId.get(unpluralizedOption != null ? unpluralizedOption : option);
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


  public String getInnerPath(SearchableConfigurable configurable, String option){
    return myHelpIdWithOption2Path.get(Pair.create(configurable.getId(), option));
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

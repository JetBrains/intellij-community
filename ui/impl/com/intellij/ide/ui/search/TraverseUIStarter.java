/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.ui.search;

import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;

/**
 * User: anna
 * Date: 13-Feb-2006
 */
public class TraverseUIStarter implements ApplicationStarter {
  private String OUTPUT_PATH;

  @NonNls
  public String getCommandName() {
    return "traverseUI";
  }


  @SuppressWarnings({"HardCodedStringLiteral"})
  public void premain(String[] args) {
    System.setProperty("idea.load.plugins.category", "traverseUI");
    OUTPUT_PATH = args[1];
  }

  public void main(String[] args){
    try {
      startup();
    }
    catch (IOException e) {
      //noinspection CallToPrintStackTrace
      e.printStackTrace();
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
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
        final String singleOption = StringUtil.unpluralize(option.first.toLowerCase());
        sortedOptions.add(Pair.create(singleOption != null ? singleOption : option.first.toLowerCase(), option.second));
      }
      for (Pair<String,String> option : sortedOptions) {
        Element optionElement = new Element("option");
        optionElement.setAttribute("name", option.first);
        if (option.second != null) {
          optionElement.setAttribute("path", option.second);
        }
        configurableElement.addContent(optionElement);
      }
      root.addContent(configurableElement);
    }
    JDOMUtil.writeDocument(new Document(root), OUTPUT_PATH, "\n");
  }
}

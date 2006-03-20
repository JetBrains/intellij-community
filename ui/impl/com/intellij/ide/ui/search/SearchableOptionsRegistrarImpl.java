/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.ui.search;

import com.intellij.application.options.CodeStyleSchemesConfigurable;
import com.intellij.application.options.ProjectCodeStyleConfigurable;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
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
  private Set<OptionDescription> myHints = new TreeSet<OptionDescription>();
  private Map<Pair<String,String>, Set<String>> myHelpIdWithOption2Path = new HashMap<Pair<String, String>, Set<String>>();
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
        final String groupName = configurable.getAttributeValue("configurable_name");
        final List options = configurable.getChildren("option");
        for (Object o1 : options) {
          Element optionElement = (Element)o1;
          final String option = optionElement.getAttributeValue("name");
          final String path = optionElement.getAttributeValue("path");
          final String hit = optionElement.getAttributeValue("hit");
          putOptionWithHelpId(option, id, groupName, hit, path);
        }
      }

      //synonyms
      document = JDOMUtil.loadDocument(ResourceUtil.getResource(SearchableOptionsRegistrar.class, "/search/", "synonyms.xml"));
      root = document.getRootElement();
      configurables = root.getChildren("configurable");
      for (final Object o : configurables) {
        final Element configurable = (Element)o;
        final String id = configurable.getAttributeValue("id");
        final String groupName = configurable.getAttributeValue("configurable_name");
        final List synonyms = configurable.getChildren("synonym");
        for (Object o1 : synonyms) {
          Element synonymElement = (Element)o1;
          final String synonym = synonymElement.getTextNormalize();
          if (synonym != null) {
            putOptionWithHelpId(synonym, id, groupName, null, null);
          }
        }
        final List options = configurable.getChildren("option");
        for (Object o1 : options) {
          Element optionElement = (Element)o1;
          final String option = optionElement.getAttributeValue("name");
          final List list = optionElement.getChildren("synonym");
          for (Object o2 : list) {
            Element synonymElement = (Element)o2;
            final String synonym = synonymElement.getTextNormalize();
            if (synonym != null) {
              putOptionWithHelpId(synonym, id, groupName, null, null);
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

  private void putOptionWithHelpId(String option, final String id, final String groupName, String hit, final String path) {
    option = PorterStemmerUtil.stem(option);
    if (option == null) return;
    final OptionDescription description = new OptionDescription(option, hit, path);
    description.setGroupName(groupName);
    myHints.add(description);
    Set<String> helpIds = myOption2HelpId.get(option);
    if (helpIds == null) {
      helpIds = new HashSet<String>();
      myOption2HelpId.put(option, helpIds);
    }
    helpIds.add(id);

    Set<String> paths = myHelpIdWithOption2Path.get(Pair.create(id, option));
    if (paths == null) {
      paths = new HashSet<String>();
      myHelpIdWithOption2Path.put(Pair.create(id, option), paths);
    }
    paths.add(path);
  }

  @NotNull
  public Set<Configurable> getConfigurables(ConfigurableGroup[] configurables, String option, boolean showProjectCodeStyle) {
    Set<String> options = getProcessedWords(option);
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
    Set<String> path = null;
    final Set<String> words = getProcessedWords(option);
    for (String word : words) {
      final Set<String> paths = myHelpIdWithOption2Path.get(Pair.create(configurable.getId(), word));
      if (paths == null) return null;
      if (path == null){
        path = paths;
      }
      path.retainAll(paths);
    }
    return path == null || path.isEmpty() ? null : path.iterator().next();
  }

  public boolean isStopWord(String word){
    return myStopWords.contains(word);
  }

  public String getSynonym(final String option, final SearchableConfigurable configurable) {
    return myHighlightSynonym2Option.get(Pair.create(option, configurable.getId()));
  }

  public Map<String, TreeSet<OptionDescription>> findPossibleExtension(String prefix, final Project project) {
    final boolean perProject = CodeStyleSettingsManager.getInstance(project).USE_PER_PROJECT_SETTINGS;
    final Map<String, TreeSet<OptionDescription>> result = new TreeMap<String, TreeSet<OptionDescription>>();
    final Set<String> prefixes = getProcessedWords(prefix);
    for (String opt : prefixes) {
      Set<String> hints = new HashSet<String>();
      for (OptionDescription description : myHints) {
        if (description != null) {
          final String hit = description.getHit();
          if (hit != null) {
            final Set<String> words = getProcessedWords(hit);
            for (String word : words) {
              if (word.startsWith(opt)){
                String groupName = description.getGroupName();
                if (perProject) {
                  if (Comparing.strEqual(groupName, ApplicationBundle.message("title.global.code.style"))){
                    groupName = ApplicationBundle.message("title.project.code.style");
                  }
                } else {
                  if (Comparing.strEqual(groupName, ApplicationBundle.message("title.project.code.style"))){
                    groupName = ApplicationBundle.message("title.global.code.style");
                  }
                }
                TreeSet<OptionDescription> descriptions = result.get(groupName);
                if (descriptions == null){
                  descriptions = new TreeSet<OptionDescription>();
                  result.put(groupName, descriptions);
                }
                if (!hints.contains(hit.toLowerCase())){
                  descriptions.add(description);
                }
                hints.add(hit.toLowerCase());
                break;
              }
            }
          }
        }
      }
    }
    return result;
  }

  public void addOption(SearchableConfigurable configurable, String option, String path, final String hit) {
    putOptionWithHelpId(option, configurable.getId(), configurable.getDisplayName(), hit, path);
  }

  @NonNls
  public String getComponentName() {
    return "SearchableOptionsRegistrar";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public Set<String> getProcessedWordsWithoutStemming(@NotNull String text){
    Set<String> result = new HashSet<String>();
    @NonNls final String toLowerCase = text.toLowerCase();
    final String[] options = toLowerCase.split("[\\W&&[^_-]]");
    if (options != null) {
      for (String opt : options) {
        if (opt == null) continue;
        if (isStopWord(opt)) continue;
        result.add(opt);
      }
    }
    return result;
  }

  public Set<String> getProcessedWords(@NotNull String text){
    Set<String> result = new HashSet<String>();
    @NonNls final String toLowerCase = text.toLowerCase();
    final String[] options = toLowerCase.split("[\\W&&[^_-]]");
    if (options != null) {
      for (String opt : options) {
        if (isStopWord(opt)) continue;
        opt = PorterStemmerUtil.stem(opt);
        if (opt == null) continue;
        result.add(opt);
      }
    }
    return result;
  }

  public Set<String> replaceSynonyms(Set<String> options, SearchableConfigurable configurable){
    final Set<String> result = new HashSet<String>(options);
    for (String option : options) {
      final String synonym = getSynonym(option, configurable);
      if (synonym != null) {
        result.add(synonym);
      } else {
        result.add(option);
      }
    }
    return result;
  }
}

/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.ui.search;

import com.intellij.application.options.CodeStyleSchemesConfigurable;
import com.intellij.application.options.ProjectCodeStyleConfigurable;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.ResourceUtil;
import com.intellij.util.containers.StringInterner;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.DocumentEvent;
import java.util.*;

/**
 * User: anna
 * Date: 07-Feb-2006
 */
public class SearchableOptionsRegistrarImpl extends SearchableOptionsRegistrar implements ApplicationComponent {

  private Map<String, Set<OptionDescription>> myStorage = new THashMap<String, Set<OptionDescription>>(1500, 0.9f);
  private Map<String, String> myId2Name = new THashMap<String, String>(20, 0.9f);

  private Set<String> myStopWords = new HashSet<String>();
  private Map<Pair<String, String>, Set<String>> myHighlightOption2Synonym = new THashMap<Pair<String, String>, Set<String>>();

  @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
  private StringInterner myIdentifierTable = new StringInterner();
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.ui.search.SearchableOptionsRegistrarImpl");
  public static final int LOAD_FACTOR = 20;

  @SuppressWarnings({"HardCodedStringLiteral"})
  public SearchableOptionsRegistrarImpl() {
    try {
      //stop words
      final String text = ResourceUtil.loadText(ResourceUtil.getResource(SearchableOptionsRegistrarImpl.class, "/search/", "ignore.txt"));
      final String[] stopWords = text.split("[\\W]");
      myStopWords.addAll(Arrays.asList(stopWords));

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
            Set<String> words = getProcessedWords(synonym);
            for (String word : words) {
              putOptionWithHelpId(word, id, groupName, synonym, null);
            }
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
              Set<String> words = getProcessedWords(synonym);
              for (String word : words) {
                putOptionWithHelpId(word, id, groupName, synonym, null);
              }
              final Pair<String, String> key = Pair.create(option, id);
              Set<String> foundSynonyms = myHighlightOption2Synonym.get(key);
              if (foundSynonyms == null) {
                foundSynonyms = new THashSet<String>();
                myHighlightOption2Synonym.put(key, foundSynonyms);
              }
              foundSynonyms.add(synonym);
            }
          }
        }
      }


    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  private void putOptionWithHelpId(String option, final String id, final String groupName, String hit, final String path) {
    if (myStopWords.contains(option)) return;
    String stopWord = PorterStemmerUtil.stem(option);
    if (stopWord == null) return;
    if (myStopWords.contains(stopWord)) return;
    if (!myId2Name.containsKey(id) && groupName != null) {
      myId2Name.put(myIdentifierTable.intern(id), myIdentifierTable.intern(groupName));
    }

    Set<OptionDescription> configs = myStorage.get(option);
    if (configs == null) {
      configs = new THashSet<OptionDescription>(3, 0.9f);
      myStorage.put(option, configs);
    }

    configs.add(new OptionDescription(null, myIdentifierTable.intern(id), hit != null ? myIdentifierTable.intern(hit) : null,
                                      path != null ? myIdentifierTable.intern(path) : null));
  }

  @NotNull
  public Set<Configurable> getConfigurables(ConfigurableGroup[] groups,
                                            final DocumentEvent.EventType type,
                                            Set<Configurable> configurables,
                                            String option,
                                            boolean showProjectCodeStyle) {

    Set<String> options = getProcessedWordsWithoutStemming(option);
    if (configurables == null) {
      configurables = new HashSet<Configurable>();
      for (ConfigurableGroup group : groups) {
        configurables.addAll(Arrays.asList(group.getConfigurables()));
      }
    }
    final Set<Configurable> currentConfigurables = new HashSet<Configurable>(configurables);
    Set<String> helpIds = null;
    if (options.isEmpty()) { //operate with substring
      options.add(option);
    }
    for (String opt : options) {
      final Set<OptionDescription> optionIds = getAcceptableDescriptions(opt);
      if (optionIds == null) {
        configurables.clear();
        return configurables;
      }
      final Set<String> ids = new HashSet<String>();
      for (OptionDescription id : optionIds) {
        ids.add(id.getConfigurableId());
      }
      if (helpIds == null) {
        helpIds = ids;
      }
      helpIds.retainAll(ids);
    }
    if (helpIds != null) {
      for (Iterator<Configurable> it = configurables.iterator(); it.hasNext();) {
        Configurable configurable = it.next();
        if ((configurable instanceof ProjectCodeStyleConfigurable && !showProjectCodeStyle) ||
            (configurable instanceof CodeStyleSchemesConfigurable && showProjectCodeStyle)) {
          it.remove();
          continue;
        }
        if (!(configurable instanceof SearchableConfigurable && helpIds.contains(((SearchableConfigurable)configurable).getId()))) {
          it.remove();
        }
      }
    }
    if (type == DocumentEvent.EventType.REMOVE && currentConfigurables.equals(configurables)) {
      return getConfigurables(groups, DocumentEvent.EventType.CHANGE, null, option, showProjectCodeStyle);
    }
    return configurables;
  }

  @Nullable
  private Set<OptionDescription> getAcceptableDescriptions(final String prefix) {
    if (prefix == null) return null;
    final String stemmedPrefix = PorterStemmerUtil.stem(prefix);
    if (stemmedPrefix == null) return null;
    Set<OptionDescription> result = null;
    for (String option : myStorage.keySet()) {
      if (option.startsWith(prefix) || option.startsWith(stemmedPrefix)) {
        if (result == null) {
          result = new THashSet<OptionDescription>();
        }
        result.addAll(myStorage.get(option));
      }
    }
    return result;
  }

  @Nullable
  public String getInnerPath(SearchableConfigurable configurable, @NonNls String option) {
    Set<OptionDescription> path = null;
    final Set<String> words = getProcessedWordsWithoutStemming(option);
    for (String word : words) {
      Set<OptionDescription> configs = getAcceptableDescriptions(word);
      if (configs == null) return null;
      final Set<OptionDescription> paths = new HashSet<OptionDescription>();
      for (OptionDescription config : configs) {
        if (Comparing.strEqual(config.getConfigurableId(), configurable.getId())) {
          paths.add(config);
        }
      }
      if (path == null) {
        path = paths;
      }
      path.retainAll(paths);
    }
    if (path == null || path.isEmpty()) {
      return null;
    }
    else {
      OptionDescription result = null;
      for (OptionDescription description : path) {
        final String hit = description.getHit();
        if (hit != null) {
          boolean theBest = true;
          for (String word : words) {
            if (hit.indexOf(word) == -1) {
              theBest = false;
            }
          }
          if (theBest) return description.getPath();
        }
        result = description;
      }
      return result != null ? result.getPath() : null;
    }
  }

  public boolean isStopWord(String word) {
    return myStopWords.contains(word);
  }

  public Set<String> getSynonym(final String option, @NotNull final SearchableConfigurable configurable) {
    return myHighlightOption2Synonym.get(Pair.create(option, configurable.getId()));
  }

  public Map<String, Set<String>> findPossibleExtension(@NotNull String prefix, final Project project) {
    final boolean perProject = CodeStyleSettingsManager.getInstance(project).USE_PER_PROJECT_SETTINGS;
    final Map<String, Set<String>> result = new THashMap<String, Set<String>>();
    int count = 0;
    final Set<String> prefixes = getProcessedWordsWithoutStemming(prefix);
    for (String opt : prefixes) {
      Set<OptionDescription> configs = getAcceptableDescriptions(opt);
      if (configs == null) continue;
      for (OptionDescription description : configs) {
        String groupName = myId2Name.get(description.getConfigurableId());
        if (perProject) {
          if (Comparing.strEqual(groupName, ApplicationBundle.message("title.global.code.style"))) {
            groupName = ApplicationBundle.message("title.project.code.style");
          }
        }
        else {
          if (Comparing.strEqual(groupName, ApplicationBundle.message("title.project.code.style"))) {
            groupName = ApplicationBundle.message("title.global.code.style");
          }
        }
        Set<String> foundHits = result.get(groupName);
        if (foundHits == null) {
          foundHits = new THashSet<String>();
          result.put(groupName, foundHits);
        }
        foundHits.add(description.getHit());
        count++;
      }
    }
    if (count > LOAD_FACTOR) {
      result.clear();
    }
    return result;
  }

  public void addOption(SearchableConfigurable configurable, String option, String path, final String hit) {
    putOptionWithHelpId(option, configurable.getId(), configurable.getDisplayName(), hit, path);
  }

  @NotNull
  @NonNls
  public String getComponentName() {
    return "SearchableOptionsRegistrar";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public Set<String> getProcessedWordsWithoutStemming(@NotNull String text) {
    Set<String> result = new HashSet<String>();
    @NonNls final String toLowerCase = text.toLowerCase();
    final String[] options = toLowerCase.split("[\\W&&[^_-]]");
    if (options != null) {
      for (String opt : options) {
        if (opt == null) continue;
        if (isStopWord(opt)) continue;
        final String processed = PorterStemmerUtil.stem(opt);
        if (isStopWord(processed)) continue;
        result.add(opt);
      }
    }
    return result;
  }

  public Set<String> getProcessedWords(@NotNull String text) {
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

  public Set<String> replaceSynonyms(Set<String> options, SearchableConfigurable configurable) {
    final Set<String> result = new HashSet<String>(options);
    for (String option : options) {
      final Set<String> synonyms = getSynonym(option, configurable);
      if (synonyms != null) {
        result.addAll(synonyms);
      }
      else {
        result.add(option);
      }
    }
    return result;
  }
}

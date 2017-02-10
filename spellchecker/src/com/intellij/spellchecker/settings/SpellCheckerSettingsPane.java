/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.spellchecker.settings;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.profile.codeInspection.ui.ErrorsConfigurable;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.dictionary.EditableDictionary;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.spellchecker.util.SPFileUtil;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.spellchecker.util.Strings;
import com.intellij.ui.AddDeleteListPanel;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.OptionalChooserComponent;
import com.intellij.ui.PathsChooserComponent;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.util.*;
import java.util.List;

public class SpellCheckerSettingsPane implements Disposable {
  private JPanel root;
  private JPanel linkContainer;
  private JPanel panelForDictionaryChooser;
  private JPanel panelForAcceptedWords;
  private JPanel panelForFolderChooser;
  private OptionalChooserComponent<String> optionalChooserComponent;
  private PathsChooserComponent pathsChooserComponent;
  private final List<Pair<String, Boolean>> allDictionaries = new ArrayList<>();
  private final List<String> dictionariesFolders = new ArrayList<>();
  private final List<String> removedDictionaries = new ArrayList<>();
  private final WordsPanel wordsPanel;
  private final SpellCheckerManager manager;
  private final SpellCheckerSettings settings;

  public SpellCheckerSettingsPane(SpellCheckerSettings settings, final Project project) {
    this.settings = settings;
    manager = SpellCheckerManager.getInstance(project);
    HyperlinkLabel link = new HyperlinkLabel(SpellCheckerBundle.message("link.to.inspection.settings"));
    link.addHyperlinkListener(new HyperlinkListener() {
      public void hyperlinkUpdate(final HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          Settings allSettings = Settings.KEY.getData(DataManager.getInstance().getDataContext());
          if (allSettings != null) {
            final ErrorsConfigurable errorsConfigurable = allSettings.find(ErrorsConfigurable.class);
            if (errorsConfigurable != null) {
              allSettings.select(errorsConfigurable).doWhenDone(
                () -> errorsConfigurable.selectInspectionTool(SpellCheckingInspection.SPELL_CHECKING_INSPECTION_TOOL_NAME));
            }
          }
        }
      }
    });
    linkContainer.setLayout(new BorderLayout());
    linkContainer.add(link);

    // Fill in all the dictionaries folders (not implemented yet) and enabled dictionaries
    fillAllDictionaries();


    pathsChooserComponent = new PathsChooserComponent(dictionariesFolders, new PathsChooserComponent.PathProcessor() {
      public boolean addPath(List<String> paths, String path) {
        if (paths.contains(path)) {
          final String title = SpellCheckerBundle.message("add.directory.title");
          final String msg = SpellCheckerBundle.message("directory.is.already.included");
          Messages.showErrorDialog(root, msg, title);
          return false;
        }
        paths.add(path);

        final ArrayList<Pair<String, Boolean>> currentDictionaries = optionalChooserComponent.getCurrentModel();
        SPFileUtil.processFilesRecursively(path, s -> currentDictionaries.add(Pair.create(s, true)));
        optionalChooserComponent.refresh();
        return true;
      }

      public boolean removePath(List<String> paths, String path) {
        if (paths.remove(path)) {
          final ArrayList<Pair<String, Boolean>> result = new ArrayList<>();
          final ArrayList<Pair<String, Boolean>> currentDictionaries = optionalChooserComponent.getCurrentModel();
          for (Pair<String, Boolean> pair : currentDictionaries) {
            if (!pair.first.startsWith(FileUtil.toSystemDependentName(path))) {
              result.add(pair);
            } else {
              removedDictionaries.add(pair.first);
            }
          }
          currentDictionaries.clear();
          currentDictionaries.addAll(result);
          optionalChooserComponent.refresh();
          return true;
        }
        return false;
      }
    }, project);
    panelForFolderChooser.setLayout(new BorderLayout());
    panelForFolderChooser.add(pathsChooserComponent.getContentPane(), BorderLayout.CENTER);
    pathsChooserComponent.getEmptyText().setText(SpellCheckerBundle.message("no.custom.folders"));

    optionalChooserComponent = new OptionalChooserComponent<String>(allDictionaries) {
      @Override
      public JCheckBox createCheckBox(String path, boolean checked) {
        if (isUserDictionary(path)) {
          path = FileUtil.toSystemIndependentName(path);
          final int i = path.lastIndexOf('/');
          if (i != -1) {
            final String name = path.substring(i + 1);
            return new JCheckBox("[user] " + name, checked);
          }
        }
        return new JCheckBox("[bundled] " + FileUtil.toSystemDependentName(path), checked);
      }
    };

    panelForDictionaryChooser.setLayout(new BorderLayout());
    panelForDictionaryChooser.add(optionalChooserComponent.getContentPane(), BorderLayout.CENTER);
    optionalChooserComponent.getEmptyText().setText(SpellCheckerBundle.message("no.dictionaries"));


    wordsPanel = new WordsPanel(manager);
    panelForAcceptedWords.setLayout(new BorderLayout());
    panelForAcceptedWords.add(wordsPanel, BorderLayout.CENTER);

  }

  public JComponent getPane() {
    return root;
  }

  public boolean isModified() {
    return wordsPanel.isModified() || optionalChooserComponent.isModified() || pathsChooserComponent.isModified();
  }

  public void apply() throws ConfigurationException {
    if (wordsPanel.isModified()){
     manager.updateUserDictionary(wordsPanel.getWords());
    }
    if (!optionalChooserComponent.isModified() && !pathsChooserComponent.isModified()){
      return;
    }

    optionalChooserComponent.apply();
    pathsChooserComponent.apply();
    settings.setDictionaryFoldersPaths(pathsChooserComponent.getValues());

    final HashSet<String> disabledDictionaries = new HashSet<>();
    final HashSet<String> bundledDisabledDictionaries = new HashSet<>();
    for (Pair<String, Boolean> pair : allDictionaries) {
      if (!pair.second) {
        final String scriptPath = pair.first;
        if (isUserDictionary(scriptPath)) {
          disabledDictionaries.add(scriptPath);
        }
        else {
          bundledDisabledDictionaries.add(scriptPath);
        }
      }

    }
    settings.setDisabledDictionariesPaths(disabledDictionaries);
    settings.setBundledDisabledDictionariesPaths(bundledDisabledDictionaries);

    manager.updateBundledDictionaries(removedDictionaries);
  }

  private boolean isUserDictionary(final String dictionary) {
    boolean isUserDictionary = false;
    for (String dictionaryFolder : pathsChooserComponent.getValues()) {
      if (FileUtil.toSystemIndependentName(dictionary).startsWith(dictionaryFolder)) {
        isUserDictionary = true;
        break;
      }
    }
    return isUserDictionary;

  }

  public void reset() {
    pathsChooserComponent.reset();
    fillAllDictionaries();
    optionalChooserComponent.reset();
    removedDictionaries.clear();
  }


  private void fillAllDictionaries() {
    dictionariesFolders.clear();
    dictionariesFolders.addAll(settings.getDictionaryFoldersPaths());
    allDictionaries.clear();
    for (String dictionary : SpellCheckerManager.getBundledDictionaries()) {
      allDictionaries.add(Pair.create(dictionary, !settings.getBundledDisabledDictionariesPaths().contains(dictionary)));
    }

    // user
    //todo [shkate]: refactoring  - SpellCheckerManager contains the same code withing reloadConfiguration()
    final Set<String> disabledDictionaries = settings.getDisabledDictionariesPaths();
    for (String folder : dictionariesFolders) {
      SPFileUtil.processFilesRecursively(folder, s -> allDictionaries.add(Pair.create(s, !disabledDictionaries.contains(s))));
    }
  }


  public void dispose() {
    if (wordsPanel != null) {
      wordsPanel.dispose();
    }
  }

  public static final class WordDescriber {
    private final EditableDictionary dictionary;

    public WordDescriber(EditableDictionary dictionary) {
      this.dictionary = dictionary;
    }

    @NotNull
    public List<String> process() {
      if (this.dictionary == null) {
        return new ArrayList<>();
      }
      Set<String> words = this.dictionary.getEditableWords();
      if (words == null) {
        return new ArrayList<>();
      }
      List<String> result = new ArrayList<>();
      for (String word : words) {
        result.add(word);
      }
      Collections.sort(result);
      return result;
    }
  }

  private static final class WordsPanel extends AddDeleteListPanel implements Disposable {
    private final SpellCheckerManager manager;

    private WordsPanel(SpellCheckerManager manager) {
      super(null, new WordDescriber(manager.getUserDictionary()).process());
      this.manager = manager;
      getEmptyText().setText(SpellCheckerBundle.message("no.words"));
    }


    protected Object findItemToAdd() {
      String word = Messages.showInputDialog(SpellCheckerBundle.message("enter.simple.word"),
                                             SpellCheckerBundle.message("add.new.word"), null);
      if (word == null) {
        return null;
      }
      else {
        word = word.trim();
      }

      if (Strings.isMixedCase(word)) {
        Messages.showWarningDialog(SpellCheckerBundle.message("entered.word.0.is.mixed.cased.you.must.enter.simple.word", word),
                                   SpellCheckerBundle.message("add.new.word"));
        return null;
      }
      if (!manager.hasProblem(word)) {
        Messages.showWarningDialog(SpellCheckerBundle.message("entered.word.0.is.correct.you.no.need.to.add.this.in.list", word),
                                   SpellCheckerBundle.message("add.new.word"));
        return null;
      }
      return word;
    }


    public void dispose() {
      myListModel.removeAllElements();
    }

    @Nullable
    public List<String> getWords() {
      Object[] pairs = getListItems();
      if (pairs == null) {
        return null;
      }
      List<String> words = new ArrayList<>();
      for (Object pair : pairs) {
        words.add(pair.toString());
      }
      return words;
    }

    public boolean isModified() {
      List<String> newWords = getWords();
      Set<String> words = manager.getUserDictionary().getEditableWords();
      if (words == null && newWords == null) {
        return false;
      }
      if (words == null || newWords == null || newWords.size() != words.size()) {
        return true;
      }
      return !(words.containsAll(newWords) && newWords.containsAll(words));
    }
  }


}

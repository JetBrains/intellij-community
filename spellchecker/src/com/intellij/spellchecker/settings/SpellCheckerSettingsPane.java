// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.settings;

import com.intellij.ide.DataManager;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.profile.codeInspection.ui.ErrorsConfigurable;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.spellchecker.statistics.SpellcheckerActionStatistics;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.spellchecker.util.Strings;
import com.intellij.ui.*;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.extensions.PluginId.getId;
import static com.intellij.spellchecker.SpellCheckerManager.DictionaryLevel.APP;
import static com.intellij.spellchecker.SpellCheckerManager.DictionaryLevel.PROJECT;
import static com.intellij.spellchecker.SpellCheckerManager.getBundledDictionaries;
import static com.intellij.ui.IdeBorderFactory.createTitledBorder;
import static com.intellij.util.ui.JBUI.insetsTop;
import static javax.swing.event.HyperlinkEvent.EventType.ACTIVATED;

public class SpellCheckerSettingsPane implements Disposable {
  private JPanel root;
  private JPanel linkContainer;
  private JPanel myPanelForAcceptedWords;
  private JPanel myPanelForCustomDictionaries;
  private JBCheckBox myUseSingleDictionary;
  private ComboBox<String> myDictionariesComboBox;
  private final CustomDictionariesPanel myDictionariesPanel;

  //Dictionaries provided by plugins -- runtime and bundled
  private final OptionalChooserComponent<String> myProvidedDictionariesChooserComponent;
  private final Set<String> runtimeDictionaries = new HashSet<>();
  private final List<Pair<String, Boolean>> providedDictionaries = new ArrayList<>();

  private final WordsPanel wordsPanel;
  private final SpellCheckerManager manager;
  private final SpellCheckerSettings settings;

  public SpellCheckerSettingsPane(SpellCheckerSettings settings, final Project project) {
    this.settings = settings;
    manager = SpellCheckerManager.getInstance(project);
    HyperlinkLabel link = new HyperlinkLabel(SpellCheckerBundle.message("link.to.inspection.settings"));
    link.addHyperlinkListener(e -> {
      if (e.getEventType() == ACTIVATED) {
        Settings allSettings = Settings.KEY.getData(DataManager.getInstance().getDataContext());
        if (allSettings != null) {
          final ErrorsConfigurable errorsConfigurable = allSettings.find(ErrorsConfigurable.class);
          if (errorsConfigurable != null) {
            allSettings.select(errorsConfigurable).doWhenDone(
              () -> errorsConfigurable.selectInspectionTool(SpellCheckingInspection.SPELL_CHECKING_INSPECTION_TOOL_NAME));
          }
        }
      }
    });
    myUseSingleDictionary.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myDictionariesComboBox.setEnabled(myUseSingleDictionary.isSelected());
      }
    });
    myDictionariesComboBox.addItem(APP.getName());
    myDictionariesComboBox.addItem(PROJECT.getName());
    linkContainer.setLayout(new BorderLayout());
    linkContainer.add(link);

    // Fill in all the dictionaries folders (not implemented yet) and enabled dictionaries
    fillProvidedDictionaries();

    myDictionariesPanel = new CustomDictionariesPanel(settings, project, manager);

    myPanelForCustomDictionaries.setBorder(
      createTitledBorder(SpellCheckerBundle.message("add.dictionary.description", getHunspellDescription()),
                                          false, insetsTop(8)).setShowLine(false));

    myPanelForAcceptedWords
      .setBorder(createTitledBorder(SpellCheckerBundle.message("settings.tab.accepted.words"), false, insetsTop(8)).setShowLine(false));
    myPanelForCustomDictionaries.setLayout(new BorderLayout());
    myPanelForCustomDictionaries.add(myDictionariesPanel, BorderLayout.CENTER);

    myProvidedDictionariesChooserComponent = new OptionalChooserComponent<String>(providedDictionaries) {
      @Override
      public JCheckBox createCheckBox(String path, boolean checked) {
        return new JCheckBox(FileUtil.toSystemDependentName(path), checked);
      }

      @Override
      public void apply() {
        super.apply();

        final HashSet<String> runtimeDisabledDictionaries = new HashSet<>();

        for (Pair<String, Boolean> pair : providedDictionaries) {
          if (pair.second) continue;

          if (runtimeDictionaries.contains(pair.first)) {
            runtimeDisabledDictionaries.add(pair.first);
          }
        }
        settings.setRuntimeDisabledDictionariesNames(runtimeDisabledDictionaries);
      }

      @Override
      public void reset() {
        super.reset();
        fillProvidedDictionaries();
      }
    };

    myProvidedDictionariesChooserComponent.getEmptyText().setText(SpellCheckerBundle.message("no.dictionaries"));


    wordsPanel = new WordsPanel(manager);
    myPanelForAcceptedWords.setLayout(new BorderLayout());
    myPanelForAcceptedWords.add(wordsPanel, BorderLayout.CENTER);
  }

  private static String getHunspellDescription() {
    final PluginId hunspellId = getId("hunspell");
    final IdeaPluginDescriptor ideaPluginDescriptor = PluginManagerCore.getPlugin(hunspellId);
    if (PluginManagerCore.isPluginInstalled(hunspellId) && ideaPluginDescriptor != null && ideaPluginDescriptor.isEnabled()) {
      return ", " + SpellCheckerBundle.message("hunspell.description");
    }
    else {
      return "";
    }
  }

  public JComponent getPane() {
    return root;
  }

  public boolean isModified() {
    return wordsPanel.isModified() ||
           myProvidedDictionariesChooserComponent.isModified() ||
           myDictionariesPanel.isModified() ||
           settings.isUseSingleDictionaryToSave() != myUseSingleDictionary.isSelected() ||
           (settings.isUseSingleDictionaryToSave() && settings.getDictionaryToSave() != myDictionariesComboBox.getSelectedItem());
  }

  public void apply() throws ConfigurationException {
    if (wordsPanel.isModified()) {
      manager.updateUserDictionary(wordsPanel.getWords());
    }
    if (settings.isUseSingleDictionaryToSave() != myUseSingleDictionary.isSelected()) {
      settings.setUseSingleDictionaryToSave(myUseSingleDictionary.isSelected());
    }
    if (myUseSingleDictionary.isSelected() && settings.getDictionaryToSave() != myDictionariesComboBox.getSelectedItem()) {
      settings.setDictionaryToSave((String)myDictionariesComboBox.getSelectedItem());
    }
    SpellCheckerManager.restartInspections();
    if (!myProvidedDictionariesChooserComponent.isModified() && !myDictionariesPanel.isModified()) {
      return;
    }

    myProvidedDictionariesChooserComponent.apply();
    myDictionariesPanel.apply();
  }

  public void reset() {
    myUseSingleDictionary.setSelected(settings.isUseSingleDictionaryToSave());
    myDictionariesComboBox.setSelectedItem(settings.getDictionaryToSave());
    myDictionariesComboBox.setEnabled(myUseSingleDictionary.isSelected());
    myDictionariesPanel.reset();
    myProvidedDictionariesChooserComponent.reset();
  }


  private void fillProvidedDictionaries() {
    providedDictionaries.clear();

    for (String dictionary : getBundledDictionaries()) {
      providedDictionaries.add(Pair.create(dictionary, true));
    }

    runtimeDictionaries.clear();
    for (String dictionary : ContainerUtil.map(SpellCheckerManager.getRuntimeDictionaries(), (it) -> it.getName())) {
      runtimeDictionaries.add(dictionary);
      providedDictionaries.add(Pair.create(dictionary, !settings.getRuntimeDisabledDictionariesNames().contains(dictionary)));
    }
  }


  @Override
  public void dispose() {
    if (wordsPanel != null) {
      Disposer.dispose(wordsPanel);
    }
  }


  private static final class WordsPanel extends AddDeleteListPanel<String> implements Disposable {
    private final SpellCheckerManager manager;

    private WordsPanel(SpellCheckerManager manager) {
      super(null, ContainerUtil.sorted(manager.getUserDictionaryWords()));
      this.manager = manager;
      getEmptyText().setText(SpellCheckerBundle.message("no.words"));
    }


    @Override
    protected void customizeDecorator(ToolbarDecorator decorator) {
      decorator.setRemoveAction((button) -> {
        SpellcheckerActionStatistics.reportAction("remove.from.accepted.words.ui", manager.getProject());
        ListUtil.removeSelectedItems(myList);
      });
    }

    @Override
    protected String findItemToAdd() {
      SpellcheckerActionStatistics.reportAction("add.to.accepted.words.ui", manager.getProject());
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


    @Override
    public void dispose() {
      myListModel.removeAllElements();
    }

    @NotNull
    public List<String> getWords() {
      Object[] pairs = getListItems();
      if (pairs == null) {
        return new ArrayList<>();
      }
      List<String> words = new ArrayList<>();
      for (Object pair : pairs) {
        words.add(pair.toString());
      }
      return words;
    }

    public boolean isModified() {
      List<String> newWords = getWords();
      Set<String> words = manager.getUserDictionaryWords();
      if (newWords.size() != words.size()) {
        return true;
      }
      return !(words.containsAll(newWords) && newWords.containsAll(words));
    }
  }
}

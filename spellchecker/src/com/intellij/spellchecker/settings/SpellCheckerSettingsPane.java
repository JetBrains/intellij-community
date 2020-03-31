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
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.spellchecker.util.Strings;
import com.intellij.ui.*;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
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
import static javax.swing.event.HyperlinkEvent.EventType.ACTIVATED;

public class SpellCheckerSettingsPane implements Disposable {

  public static final int MIN_CORRECTIONS = 1;
  public static final int MAX_CORRECTIONS = 15;
  private JPanel root;
  private JPanel linkContainer;
  private JPanel myPanelForBundledDictionaries;
  private JPanel panelForAcceptedWords;
  private JPanel myPanelForCustomDictionaries;
  private JSpinner myMaxCorrectionsSpinner;
  private JBCheckBox myUseSingleDictionary;
  private ComboBox<String> myDictionariesComboBox;
  private JPanel myAdvancedSettingsPanel;
  private JPanel myAdvancedSettingsPlaceHolder;
  private final CustomDictionariesPanel myDictionariesPanel;

  //Dictionaries provided by plugins -- runtime and bundled
  private final OptionalChooserComponent<String> myProvidedDictionariesChooserComponent;
  private final Set<String> bundledDictionaries = new HashSet<>();
  private final Set<String> runtimeDictionaries = new HashSet<>();
  private final List<Pair<String, Boolean>> providedDictionaries = new ArrayList<>();

  private final WordsPanel wordsPanel;
  private final SpellCheckerManager manager;
  private final SpellCheckerSettings settings;
  private final HideableDecorator decorator;

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
    myMaxCorrectionsSpinner.setModel(new SpinnerNumberModel(1, MIN_CORRECTIONS, MAX_CORRECTIONS, 1));
    myDictionariesComboBox.addItem(APP.getName());
    myDictionariesComboBox.addItem(PROJECT.getName());
    linkContainer.setLayout(new BorderLayout());
    linkContainer.add(link);

    // Fill in all the dictionaries folders (not implemented yet) and enabled dictionaries
    fillProvidedDictionaries();

    myDictionariesPanel = new CustomDictionariesPanel(settings, project, manager);

    myPanelForCustomDictionaries.setBorder(
      IdeBorderFactory.createTitledBorder(SpellCheckerBundle.message("add.dictionary.description", getHunspellDescription()),
                                          false, JBUI.insetsTop(8)).setShowLine(false));
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

        final HashSet<String> bundledDisabledDictionaries = new HashSet<>();
        final HashSet<String> runtimeDisabledDictionaries = new HashSet<>();

        for (Pair<String, Boolean> pair : providedDictionaries) {
          if (pair.second) continue;

          if (bundledDictionaries.contains(pair.first)) {
            bundledDisabledDictionaries.add(pair.first);
          }
          else if (runtimeDictionaries.contains(pair.first)) {
            runtimeDisabledDictionaries.add(pair.first);
          }
        }
        settings.setBundledDisabledDictionariesPaths(bundledDisabledDictionaries);
        settings.setRuntimeDisabledDictionariesNames(runtimeDisabledDictionaries);
      }

      @Override
      public void reset() {
        super.reset();
        fillProvidedDictionaries();
      }
    };

    myPanelForBundledDictionaries.setBorder(
      IdeBorderFactory.createTitledBorder(SpellCheckerBundle.message("dictionaries.panel.description"), false, JBUI.insetsTop(8)).setShowLine(false));
    myPanelForBundledDictionaries.setLayout(new BorderLayout());
    myPanelForBundledDictionaries.add(myProvidedDictionariesChooserComponent.getContentPane(), BorderLayout.CENTER);
    myProvidedDictionariesChooserComponent.getEmptyText().setText(SpellCheckerBundle.message("no.dictionaries"));


    wordsPanel = new WordsPanel(manager);
    panelForAcceptedWords.setLayout(new BorderLayout());
    panelForAcceptedWords.add(wordsPanel, BorderLayout.CENTER);
    decorator = new HideableDecorator(myAdvancedSettingsPlaceHolder, SpellCheckerBundle.message("advanced.settings"), false);
    decorator.setContentComponent(myAdvancedSettingsPanel);
    decorator.setOn(!settings.isDefaultAdvancedSettings());
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
           settings.getCorrectionsLimit() != getLimit() ||
           settings.isUseSingleDictionaryToSave() != myUseSingleDictionary.isSelected() ||
           (settings.isUseSingleDictionaryToSave() && settings.getDictionaryToSave() != myDictionariesComboBox.getSelectedItem());
  }

  public void apply() throws ConfigurationException {
    if (wordsPanel.isModified()){
     manager.updateUserDictionary(wordsPanel.getWords());
    }
    if (settings.getCorrectionsLimit() != getLimit()) {
      settings.setCorrectionsLimit(getLimit());
    }
    if (settings.isUseSingleDictionaryToSave() != myUseSingleDictionary.isSelected()) {
      settings.setUseSingleDictionaryToSave(myUseSingleDictionary.isSelected());
    }
    if (myUseSingleDictionary.isSelected() && settings.getDictionaryToSave() != myDictionariesComboBox.getSelectedItem()) {
      settings.setDictionaryToSave((String)myDictionariesComboBox.getSelectedItem());
    }
    SpellCheckerManager.restartInspections();
    if (!myProvidedDictionariesChooserComponent.isModified() && !myDictionariesPanel.isModified()){
      return;
    }

    myProvidedDictionariesChooserComponent.apply();
    myDictionariesPanel.apply();

    manager.updateBundledDictionaries(myDictionariesPanel.getRemovedDictionaries());
  }

  private int getLimit() {
    return ((SpinnerNumberModel)myMaxCorrectionsSpinner.getModel()).getNumber().intValue();
  }

  public void reset() {
    myMaxCorrectionsSpinner.setValue(settings.getCorrectionsLimit());
    myUseSingleDictionary.setSelected(settings.isUseSingleDictionaryToSave());
    myDictionariesComboBox.setSelectedItem(settings.getDictionaryToSave());
    myDictionariesComboBox.setEnabled(myUseSingleDictionary.isSelected());
    myDictionariesPanel.reset();
    myProvidedDictionariesChooserComponent.reset();
  }


  private void fillProvidedDictionaries() {
    providedDictionaries.clear();

    bundledDictionaries.clear();
    for (String dictionary : SpellCheckerManager.getBundledDictionaries()) {
      bundledDictionaries.add(dictionary);
      providedDictionaries.add(Pair.create(dictionary, !settings.getBundledDisabledDictionariesPaths().contains(dictionary)));
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
    protected String findItemToAdd() {
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

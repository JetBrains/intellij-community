// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.settings;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.ui.ErrorsConfigurable;
import com.intellij.spellchecker.DictionaryLayersProvider;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.dictionary.CustomDictionaryProvider;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.spellchecker.statistics.SpellcheckerActionStatistics;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.ui.*;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class SpellCheckerSettingsPane implements Disposable {
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
    });
    myUseSingleDictionary.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myDictionariesComboBox.setEnabled(myUseSingleDictionary.isSelected());
      }
    });
    DictionaryLayersProvider.getAllLayers(project).forEach(it -> myDictionariesComboBox.addItem(it.getName()));
    linkContainer.setLayout(new BorderLayout());
    linkContainer.add(link);

    // Fill in all the dictionaries folders (not implemented yet) and enabled dictionaries
    fillProvidedDictionaries();

    myDictionariesPanel = new CustomDictionariesPanel(settings, project, manager);

    myPanelForCustomDictionaries.setBorder(
      IdeBorderFactory.createTitledBorder(SpellCheckerBundle.message("add.dictionary.description", getSupportedDictionariesDescription()),
                                          false, JBUI.insetsTop(8)).setShowLine(false));

    myPanelForAcceptedWords
      .setBorder(IdeBorderFactory.createTitledBorder(SpellCheckerBundle.message("settings.tab.accepted.words"), false, JBUI.insetsTop(8)).setShowLine(false));
    myPanelForCustomDictionaries.setLayout(new BorderLayout());
    myPanelForCustomDictionaries.add(myDictionariesPanel, BorderLayout.CENTER);

    myProvidedDictionariesChooserComponent = new OptionalChooserComponent<>(providedDictionaries) {
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

  private static String getSupportedDictionariesDescription() {
    final String supported = CustomDictionaryProvider.EP_NAME.getExtensionList().stream()
      .map(ext -> ext.getDictionaryType())
      .collect(Collectors.joining(", "));

    return supported.isEmpty() ? supported : ", " + supported;
  }

  public JComponent getPane() {
    return root;
  }

  public boolean isModified() {
    return wordsPanel.isModified() ||
           myProvidedDictionariesChooserComponent.isModified() ||
           myDictionariesPanel.isModified() ||
           settings.isUseSingleDictionaryToSave() != myUseSingleDictionary.isSelected() ||
           (settings.isUseSingleDictionaryToSave() &&
            !StringUtil.equals(settings.getDictionaryToSave(), (String)myDictionariesComboBox.getSelectedItem()));
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
    SpellCheckerManager.Companion.restartInspections();
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

    for (String dictionary : SpellCheckerManager.getBundledDictionaries()) {
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
        SpellcheckerActionStatistics.removeWordFromAcceptedWords(manager.getProject());
        ListUtil.removeSelectedItems(myList);
      });
    }

    @Override
    protected String findItemToAdd() {
      SpellcheckerActionStatistics.addWordToAcceptedWords(manager.getProject());
      String word = Messages.showInputDialog(SpellCheckerBundle.message("enter.simple.word"),
                                             SpellCheckerBundle.message("add.new.word"), null);
      if (word == null) {
        return null;
      }
      else {
        word = word.trim();
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

    public @NotNull List<String> getWords() {
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
      Set<String> newHashWords = new HashSet<>(newWords);
      return !newHashWords.equals(words);
    }
  }
}

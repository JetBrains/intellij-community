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
package com.intellij.spellchecker.options;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.Messages;
import com.intellij.spellchecker.dictionary.Dictionary;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.spellchecker.util.Strings;
import com.intellij.ui.AddDeleteListPanel;
import com.intellij.util.containers.HashSet;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;


public class SpellCheckerOptions implements Disposable {

  private final SpellCheckerConfiguration configuration;
  private final SpellCheckerManager manager;

  private JPanel root;

  private WordsPanel userDictionaryWords;
  /*private WordsPanel ignoredWords;*/
  private JRadioButton projectRB;
  private JRadioButton localRB;
  private JLabel globalDictionaries;

  private Dictionary shownDictionary;

  public Dictionary getShownDictionary() {
    return shownDictionary;
  }

  public SpellCheckerOptions(SpellCheckerConfiguration configuration, SpellCheckerManager manager) {
    this.configuration = configuration;
    this.manager = manager;
  }

  private void createUIComponents() {
    userDictionaryWords = new WordsPanel(manager.getProjectWordList().getWords(), manager);
    /*ignoredWords = new WordsPanel(manager.getProjectWordList().getIgnoredWords(), manager);*/
    shownDictionary = manager.getProjectWordList();

  }

  public Set<String> getUserDictionaryWordsSet() {
    return getWords(userDictionaryWords);
  }

  public boolean useProjectDictionary() {
    return projectRB.isSelected();
  }

  public void setUserDictionaryWords(Set<String> words) {
    userDictionaryWords.replaceAll(words);
  }

  /*public Set<String> getIgnoredWords() {
    return getWords(ignoredWords);
  }*/

 /* public void setIgnoredWords(Set<String> dictionary) {
    ignoredWords.replaceAll(dictionary);
  }*/


  public JPanel getRoot() {
    projectRB.setSelected(true);
    projectRB.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        if (projectRB.isSelected()) {
          shownDictionary = manager.getProjectWordList();
          userDictionaryWords.replaceAll(shownDictionary.getWords());
          /*ignoredWords.replaceAll(shownDictionary.getIgnoredWords());*/
        }
      }
    });
    localRB.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        if (localRB.isSelected()) {
          shownDictionary = manager.getCachedWordList();
          userDictionaryWords.replaceAll(shownDictionary.getWords());
          /*ignoredWords.replaceAll(shownDictionary.getIgnoredWords());*/
        }
      }
    });

    if (manager.getDictionaries() != null) {
      String label = "Global dictionaries: ";
      for (String dic : manager.getDictionaries()) {
        label += dic + "; ";
      }
      globalDictionaries.setText(label);
    }
    return root;
  }


  private static Set<String> getWords(AddDeleteListPanel panel) {
    Set<String> words = new HashSet<String>();
    Object[] objects = panel.getListItems();
    for (Object object : objects) {
      words.add((String)object);
    }
    return words;
  }


  public void dispose() {
    userDictionaryWords.dispose();
    /*ignoredWords.dispose();*/
  }


  private static final class WordsPanel extends AddDeleteListPanel implements Disposable {
    private SpellCheckerManager manager;

    private WordsPanel(Set<String> words, SpellCheckerManager manager) {
      super(null, sort(words));
      this.manager = manager;
    }

    private static List<String> sort(Set<String> words) {
      List<String> arrayList = new ArrayList<String>(words);
      Collections.sort(arrayList);
      return arrayList;
    }



    protected Object findItemToAdd() {
      String word =
        Messages.showInputDialog(SpellCheckerBundle.message("enter.simple.word"), SpellCheckerBundle.message("add.new.word"), null);
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

    public void replaceAll(Set<String> words) {
      myList.clearSelection();
      myListModel.removeAllElements();
      for (String word : sort(words)) {
        myListModel.addElement(word);
      }
    }

    public void dispose() {
      myListModel.removeAllElements();
    }
  }
}

package com.intellij.spellchecker.options;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.Messages;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.spellchecker.util.Strings;
import com.intellij.ui.AddDeleteListPanel;
import com.intellij.util.containers.HashSet;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;


public class SpellCheckerOptions implements Disposable {
  private final SpellCheckerConfiguration configuration;

  private JPanel root;
  private WordsPanel userDictionaryWords;
  private WordsPanel ignoredWords;

  public SpellCheckerOptions(SpellCheckerConfiguration configuration) {
    this.configuration = configuration;
  }

  private void createUIComponents() {
    userDictionaryWords = new WordsPanel(configuration.USER_DICTIONARY_WORDS);
    ignoredWords = new WordsPanel(configuration.IGNORED_WORDS);
  }

  public Set<String> getUserDictionaryWords() {
    return getWords(userDictionaryWords);
  }

  public void setUserDictionaryWords(Set<String> words) {
    userDictionaryWords.replaceAll(words);
  }

  public Set<String> getIgnoredWords() {
    return getWords(ignoredWords);
  }

  public void setIgnoredWords(Set<String> words) {
    ignoredWords.replaceAll(words);
  }

  public JPanel getRoot() {
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
    ignoredWords.dispose();
  }

  private static final class WordsPanel extends AddDeleteListPanel implements Disposable {
    private WordsPanel(Set<String> words) {
      super(null, sort(words));
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
      SpellCheckerManager checkerManager = SpellCheckerManager.getInstance();
      if (Strings.isMixedCase(word)) {
        Messages.showWarningDialog(SpellCheckerBundle.message("entered.word.0.is.mixed.cased.you.must.enter.simple.word", word),
                                   SpellCheckerBundle.message("add.new.word"));
        return null;
      }
      if (!checkerManager.hasProblem(word)) {
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

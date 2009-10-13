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
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.dictionary.Dictionary;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.spellchecker.util.Strings;
import com.intellij.ui.AddDeleteListPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;


public class SpellCheckerOptions implements Disposable {

  private final SpellCheckerManager manager;
  private JPanel root;
  private JPanel wordPanelHolder;
  private WordsPanel wordsPanel;



  public SpellCheckerOptions(SpellCheckerManager manager) {
    this.manager = manager;
  }

  public void createUIComponents() {
    wordsPanel = new WordsPanel(manager);
    
  }

  public JPanel getRoot() {
    return root;
  }

  @Nullable
  public List<String> getWords(){
    Object[] pairs = wordsPanel.getListItems();
    if (pairs==null){
      return null;
    }
    List<String> words = new ArrayList<String>();
    for (Object pair : pairs) {
      words.add(pair.toString());
    }
    return words;
  }

  public void dispose() {
    
    wordsPanel.dispose();
  }


  public static final class WordDescriber {
    private Dictionary dictionary;

    public WordDescriber(Dictionary dictionary) {
      this.dictionary = dictionary;
    }

    @NotNull
    public List<Pair> process() {
      if (this.dictionary == null) {
        return new ArrayList<Pair>();
      }
      Set<String> words = this.dictionary.getEditableWords();
      if (words == null) {
        return new ArrayList<Pair>();
      }
      List<Pair> result = new ArrayList<Pair>();
      for (String word : words) {
        result.add(new Pair(word, ""));
      }
      Collections.sort(result);
      return result;
    }
  }

  public static final class Pair implements Comparable {
    private String word;
    private String description;

    public Pair(@NotNull String word, String description) {
      this.word = word;
      this.description = description;
    }

    public String getWord() {
      return word;
    }

    public String getDescription() {
      return description;
    }

    public int compareTo(Object o) {
      if (!(o instanceof Pair)) {
        throw new IllegalArgumentException();
      }
      return word.compareTo(((Pair)o).getWord());
    }

    @Override
    public String toString() {
      return word + (description!=null && description.trim().length()>0?"("+description+")":"");
    }
  }

  private static final class WordsPanel extends AddDeleteListPanel implements Disposable {
    private SpellCheckerManager manager;

    private WordsPanel(SpellCheckerManager manager) {
      super(null, new WordDescriber(manager.getUserDictionary()).process());
      this.manager = manager;
    }


    protected Object findItemToAdd() {
      String word = Messages.showInputDialog(com.intellij.spellchecker.util.SpellCheckerBundle.message("enter.simple.word"),
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
  }
}

/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.ui.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.GlassPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

/**
 * User: anna
 * Date: 07-Feb-2006
 */
public class SearchUtil {
  private SearchUtil() {
  }

  public static void processProjectConfigurables(Project project, HashMap<SearchableConfigurable,Set<Pair<String,String>>> options) {
    processConfigurables(project.getComponents(SearchableConfigurable.class), options);
    processApplicationConfigurables(options);
  }

  private static void processApplicationConfigurables(HashMap<SearchableConfigurable,Set<Pair<String,String>>> options) {
    processConfigurables(ApplicationManager.getApplication().getComponents(SearchableConfigurable.class), options);
  }

  public static void processConfigurables(final SearchableConfigurable[] configurables, final HashMap<SearchableConfigurable,Set<Pair<String,String>>> options) {
    for (SearchableConfigurable configurable : configurables) {
      Set<Pair<String, String>> configurableOptions = new HashSet<Pair<String, String>>();
      options.put(configurable, configurableOptions);
      final JComponent component = configurable.createComponent();
      configurableOptions.add(Pair.create(configurable.getDisplayName(), (String)null));
      processComponent(component, configurableOptions, null);
    }
  }

  public static void processComponent(final JComponent component, final Set<Pair<String,String>> configurableOptions, @NonNls String path) {
    final Border border = component.getBorder();
    if (border instanceof TitledBorder) {
      final TitledBorder titledBorder = (TitledBorder)border;
      final String title = titledBorder.getTitle();
      if (title != null) {
        processUILabel(title, configurableOptions, path);
      }
    }
    if (component instanceof JLabel) {
      final String label = ((JLabel)component).getText();
      if (label != null) {
        processUILabel(label, configurableOptions, path);
      }
    }
    else if (component instanceof JCheckBox) {
      @NonNls final String checkBoxTitle = ((JCheckBox)component).getText();
      if (checkBoxTitle != null) {
        processUILabel(checkBoxTitle, configurableOptions, path);
      }
    }
    else if (component instanceof JRadioButton) {
      @NonNls final String radioButtonTitle = ((JRadioButton)component).getText();
      if (radioButtonTitle != null) {
        processUILabel(radioButtonTitle, configurableOptions, path);
      }
    } else if (component instanceof JButton){
      @NonNls final String buttonTitle = ((JButton)component).getText();
      if (buttonTitle != null) {
        processUILabel(buttonTitle, configurableOptions, path);
      }
    }
    if (component instanceof JTabbedPane) {
      final JTabbedPane tabbedPane = (JTabbedPane)component;
      final int tabCount = tabbedPane.getTabCount();
      for (int i = 0; i < tabCount; i++) {
        final String title = path != null ? path + '.' + tabbedPane.getTitleAt(i) : tabbedPane.getTitleAt(i);
        processUILabel(title, configurableOptions, title);
        final Component tabComponent = tabbedPane.getComponentAt(i);
        if (tabComponent instanceof JComponent){
          processComponent((JComponent)tabComponent, configurableOptions, title);
        }
      }
    } else {
      final Component[] components = component.getComponents();
      if (components != null) {
        for (Component child : components) {
          if (child instanceof JComponent) {
            processComponent((JComponent)child, configurableOptions, path);
          }
        }
      }
    }
  }

  private static void processUILabel(@NonNls final String title,
                                     final Set<Pair<String, String>> configurableOptions,
                                     String path) {
    final Set<String> words = SearchableOptionsRegistrar.getInstance().getProcessedWordsWithoutStemming(title);
    for (String option : words) {
      configurableOptions.add(Pair.create(option, path));
    }
  }

  public static Runnable lightOptions(final SearchableConfigurable configurable,
                                      final JComponent component,
                                      final String option,
                                      final GlassPanel glassPanel){
    return new Runnable() {
      public void run() {
        if (!SearchUtil.traverseComponentsTree(configurable, glassPanel, component, option, true)){
          SearchUtil.traverseComponentsTree(configurable, glassPanel, component, option, false);
        }
      }
    };
  }

  public static Runnable lightOptions(final SearchableConfigurable configurable,
                                      final String option,
                                      final JComponent component,
                                      final TabbedPaneWrapper tabbedPane,
                                      final GlassPanel glassPanel){
    final Runnable runnable = SearchUtil.lightOptions(configurable, component, option, glassPanel);
    final String path = SearchableOptionsRegistrarImpl.getInstance().getInnerPath(configurable, option);
    if (path != null){
      return new Runnable() {
        public void run() {
          tabbedPane.setSelectedIndex(SearchUtil.getSelection(path, tabbedPane));
          runnable.run();
        }
      };
    }
    return runnable;
  }

  public static Runnable switchTab(String tabName, final TabbedPaneWrapper tabbedPane) {
    if (tabName == null) return null;
    final int selection = getSelection(tabName, tabbedPane);
    return selection != -1 ? new Runnable() {
      public void run() {
        tabbedPane.setSelectedIndex(selection);
      }
    } : null;
  }

  public static int getSelection(String tabIdx, final TabbedPaneWrapper tabbedPane) {
    SearchableOptionsRegistrar searchableOptionsRegistrar = SearchableOptionsRegistrar.getInstance();
    for (int i = 0; i < tabbedPane.getTabCount(); i ++) {
      final Set<String> pathWords = searchableOptionsRegistrar.getProcessedWords(tabIdx);
      final String title = tabbedPane.getTitleAt(i);
      final Set<String> titleWords = searchableOptionsRegistrar.getProcessedWords(title);
      pathWords.removeAll(titleWords);
      if (pathWords.isEmpty()) return i;
    }
    return -1;
  }

  private static boolean traverseComponentsTree(final SearchableConfigurable configurable,
                                                GlassPanel glassPanel,
                                                JComponent rootComponent,
                                                String option,
                                                boolean force){
    boolean highlight = false;
    if (rootComponent instanceof JCheckBox){
      final JCheckBox checkBox = ((JCheckBox)rootComponent);
      if (isComponentHighlighted(checkBox.getText(), option, force, configurable)){
        highlight = true;
        glassPanel.addSpotlight(checkBox);
      }
    }
    else if (rootComponent instanceof JRadioButton) {
      final JRadioButton radioButton = ((JRadioButton)rootComponent);
      if (isComponentHighlighted(radioButton.getText(), option, force, configurable)){
        highlight = true;
        glassPanel.addSpotlight(radioButton);
      }
    } else if (rootComponent instanceof JLabel){
      final JLabel label = ((JLabel)rootComponent);
      if (isComponentHighlighted(label.getText(), option, force, configurable)){
        highlight = true;
        glassPanel.addSpotlight(label);
      }
    } else if (rootComponent instanceof JButton){
      final JButton button = ((JButton)rootComponent);
      if (isComponentHighlighted(button.getText(), option, force, configurable)){
        highlight = true;
        glassPanel.addSpotlight(button);
      }
    }
    final Component[] components = rootComponent.getComponents();
    for (Component component : components) {
      if (component instanceof JComponent) {
        final boolean innerHighlight = traverseComponentsTree(configurable, glassPanel, (JComponent)component, option, force);
        if (innerHighlight){
          highlight = true;
        }
      }
    }
    return highlight;
  }

  public static boolean isComponentHighlighted(String text, String option, final boolean force, final SearchableConfigurable configurable){
    if (text == null || option == null) return false;
    SearchableOptionsRegistrar searchableOptionsRegistrar = SearchableOptionsRegistrar.getInstance();
    final Set<String> options = searchableOptionsRegistrar.replaceSynonyms(searchableOptionsRegistrar.getProcessedWords(option), configurable);
    final Set<String> tokens = searchableOptionsRegistrar.getProcessedWords(text);
    if (!force) {
      options.retainAll(tokens);
      return !options.isEmpty();
    }
    else {
      options.removeAll(tokens);
      return options.isEmpty();
    }
  }

  public static Runnable lightOptions(final SearchableConfigurable configurable,
                                      final JComponent component,
                                      final String option,
                                      final GlassPanel glassPanel,
                                      final boolean forceSelect) {
    return new Runnable() {
      public void run() {
        SearchUtil.traverseComponentsTree(configurable, glassPanel, component, option, forceSelect);
      }
    };
  }

  public static String markup(@NonNls String textToMarkup, String filter) {
    if (filter == null || filter.length() == 0){
      return textToMarkup;
    }
    final Pattern insideHtmlTagPattern = Pattern.compile("[<[^<>]*>]*<[^<>]*");
    final Set<String> options = SearchableOptionsRegistrar.getInstance().getProcessedWords(filter);
    final String[] words = textToMarkup.split("[\\W&&[^_-]]");
    for (String word : words) {
      if (options.contains(PorterStemmerUtil.stem(word.toLowerCase()))){
        final String[] splittedText = textToMarkup.split(word);
        if (splittedText != null && splittedText.length > 0) {
          textToMarkup = "";
          for (String aPart : splittedText) {
            if (aPart == null){
              aPart = "";
            }
            if (insideHtmlTagPattern.matcher(aPart).matches()){
              textToMarkup += aPart + word;
            } else {
              textToMarkup += aPart + "<font color='#ffffff' bgColor='#1d5da7'>" + word + "</font>";
            }
          }
        }
      }
    }
    return textToMarkup;
  }

  public static void appendFragments(final String filter,
                                     @NonNls String text,
                                     final int style,
                                     final Color foreground,
                                     final Color background,
                                     final ColoredTreeCellRenderer textRenderer) {
    if (text == null) return;
    if (filter == null || filter.length() == 0){
       textRenderer.append(text, new SimpleTextAttributes(style, foreground));
    } else { //markup
       final Set<String> filters = SearchableOptionsRegistrar.getInstance().getProcessedWords(filter);
       String [] words = text.split("[\\W&&[^_-]]");
       List<String> selectedWords = new ArrayList<String>();
       for (String word : words) {
         if (filters.contains(PorterStemmerUtil.stem(word.toLowerCase()))/* || word.toLowerCase().indexOf(filter.toLowerCase()) != -1*/){
           selectedWords.add(word);
         }
       }
       int idx = 0;
       for (String word : selectedWords) {
         text = text.substring(idx);
         textRenderer.append(text.substring(0, text.indexOf(word)), new SimpleTextAttributes(background, foreground, null, style));
         idx = text.indexOf(word) + word.length();
         textRenderer.append(text.substring(idx - word.length(), idx), new SimpleTextAttributes(UIUtil.getTreeSelectionBackground(), UIUtil.getTreeSelectionForeground(), null, style));
       }
       textRenderer.append(text.substring(idx, text.length()), new SimpleTextAttributes(background, foreground, null, style));
     }
   }
}

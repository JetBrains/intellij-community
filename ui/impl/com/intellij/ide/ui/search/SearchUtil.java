/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.ui.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.GlassPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.TabbedPaneWrapper;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * User: anna
 * Date: 07-Feb-2006
 */
public class SearchUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.ui.search.SearchUtil");

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

  private static void processComponent(final JComponent component, final Set<Pair<String,String>> configurableOptions, @NonNls String path) {
    SearchableOptionsRegistrar registrar = SearchableOptionsRegistrar.getInstance();
    final Border border = component.getBorder();
    if (border instanceof TitledBorder) {
      final TitledBorder titledBorder = (TitledBorder)border;
      final String title = titledBorder.getTitle();
      if (title != null) {
        processUILabel(title, configurableOptions, path, registrar);
      }
    }
    if (component instanceof JLabel) {
      final String label = ((JLabel)component).getText();
      if (label != null) {
        processUILabel(label, configurableOptions, path, registrar);
      }
    }
    else if (component instanceof JCheckBox) {
      @NonNls final String checkBoxTitle = ((JCheckBox)component).getText();
      if (checkBoxTitle != null) {
        processUILabel(checkBoxTitle, configurableOptions, path, registrar);
      }
    }
    else if (component instanceof JRadioButton) {
      @NonNls final String radioButtonTitle = ((JRadioButton)component).getText();
      if (radioButtonTitle != null) {
        processUILabel(radioButtonTitle, configurableOptions, path, registrar);
      }
    } else if (component instanceof JButton){
      @NonNls final String buttonTitle = ((JButton)component).getText();
      if (buttonTitle != null) {
        processUILabel(buttonTitle, configurableOptions, path, registrar);
      }
    }
    if (component instanceof JTabbedPane) {
      final JTabbedPane tabbedPane = (JTabbedPane)component;
      final int tabCount = tabbedPane.getTabCount();
      for (int i = 0; i < tabCount; i++) {
        final String title = tabbedPane.getTitleAt(i);
        processUILabel(title, configurableOptions, path != null ? path + "." + title : title, registrar);
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
                                     String path,
                                     final SearchableOptionsRegistrar registrar) {
    @NonNls final String text = title.toLowerCase();
    final String[] options = text.split("[\\W&&[^_-]]");
    for (String option : options) {
      if (option != null) {
        if (!registrar.isStopWord(option)) {
          configurableOptions.add(Pair.create(PorterStemmerUtil.stem(option), path));
        }
      }
    }
  }

  public static Runnable switchTab(final SearchableConfigurable configurable, final String option, final TabbedPaneWrapper tabbedPane, final GlassPanel glassPanel){
    return new Runnable() {
      public void run() {
        final String tabIndex = SearchableOptionsRegistrarImpl.getInstance().getInnerPath(configurable, option);
        LOG.assertTrue(tabIndex != null);
        tabbedPane.setSelectedIndex(new Integer(tabIndex).intValue());
        for(int i = 0; i < tabbedPane.getTabCount(); i++){
          SearchUtil.traverseComponentsTree(glassPanel, tabbedPane.getComponentAt(i), option);
        }
      }
    };
  }

  public static Runnable lightOptions(final JComponent component, final String option, final GlassPanel glassPanel){
    return new Runnable() {
      public void run() {
        SearchUtil.traverseComponentsTree(glassPanel, component, option);
      }
    };
  }

  public static Runnable lightOptions(final SearchableConfigurable configurable,
                                      final String option,
                                      final JComponent component,
                                      final TabbedPaneWrapper tabbedPane,
                                      final GlassPanel glassPanel){
    final Runnable runnable = SearchUtil.lightOptions(component, option, glassPanel);
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
    for (int i = 0; i < tabbedPane.getTabCount(); i ++) {
      if (tabIdx.compareTo(tabbedPane.getTitleAt(i)) == 0) return i;
    }
    return -1;
  }

  public static void traverseComponentsTree(GlassPanel glassPanel, JComponent rootComponent, String option){
    if (rootComponent instanceof JCheckBox){
      final JCheckBox checkBox = ((JCheckBox)rootComponent);
      if (isComponentHighlighted(checkBox.getText(), option)){
        glassPanel.addSpotlight(checkBox);
      }
    }
    else if (rootComponent instanceof JRadioButton) {
      final JRadioButton radioButton = ((JRadioButton)rootComponent);
      if (isComponentHighlighted(radioButton.getText(), option)){
        glassPanel.addSpotlight(radioButton);
      }
    } else if (rootComponent instanceof JLabel){
      final JLabel label = ((JLabel)rootComponent);
      if (isComponentHighlighted(label.getText(), option)){
        glassPanel.addSpotlight(label);
      }
    } else if (rootComponent instanceof JButton){
      final JButton button = ((JButton)rootComponent);
      if (isComponentHighlighted(button.getText(), option)){
        glassPanel.addSpotlight(button);
      }
    }
    final Component[] components = rootComponent.getComponents();
    for (Component component : components) {
      if (component instanceof JComponent) {
        traverseComponentsTree(glassPanel, (JComponent)component, option);
      }
    }
  }

  private static boolean isComponentHighlighted(String text, String option){
    SearchableOptionsRegistrar registrar = SearchableOptionsRegistrar.getInstance();
    option = PorterStemmerUtil.stem(option);
    @NonNls final String toLowerCase = text.toLowerCase();
    final String[] options = toLowerCase.split("[\\W&&[^_-]]");
    if (options != null) {
      for (String opt : options) {
        if (registrar.isStopWord(opt)) continue;
        if (Comparing.strEqual(PorterStemmerUtil.stem(opt), option)){
          return true;
        }
      }
    }
    return false;
  }

  public static Set<String> getProcessedWords(String text){
    Set<String> result = new HashSet<String>();
    @NonNls final String toLowerCase = text.toLowerCase();
    final String[] options = toLowerCase.split("[\\W&&[^_-]]");
    if (options != null) {
      final SearchableOptionsRegistrar registrar = SearchableOptionsRegistrar.getInstance();
      for (String opt : options) {
        if (registrar.isStopWord(opt)) continue;
        opt = PorterStemmerUtil.stem(opt);
        if (opt == null) continue;
        result.add(opt);
      }
    }
    return result;
  }
}

/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.ui.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.GlassPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

/**
 * User: anna
 * Date: 07-Feb-2006
 */
public class SearchUtil {
  private static final Pattern HTML_PATTERN = Pattern.compile("<[^<>]*>");

  private SearchUtil() {
  }

  public static void processProjectConfigurables(Project project, HashMap<SearchableConfigurable,TreeSet<OptionDescription>> options) {
    processConfigurables(project.getComponents(SearchableConfigurable.class), options);
    processApplicationConfigurables(options);
  }

  private static void processApplicationConfigurables(HashMap<SearchableConfigurable,TreeSet<OptionDescription>> options) {
    processConfigurables(ApplicationManager.getApplication().getComponents(SearchableConfigurable.class), options);
  }

  public static void processConfigurables(final SearchableConfigurable[] configurables, final HashMap<SearchableConfigurable,TreeSet<OptionDescription>> options) {
    for (SearchableConfigurable configurable : configurables) {
      TreeSet<OptionDescription> configurableOptions = new TreeSet<OptionDescription>();
      options.put(configurable, configurableOptions);
      final JComponent component = configurable.createComponent();
      processUILabel(configurable.getDisplayName(), configurableOptions, null);
      processComponent(component, configurableOptions, null);
    }
  }

  public static void processComponent(final JComponent component, final Set<OptionDescription> configurableOptions, @NonNls String path) {
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

  private static void processUILabel(@NonNls final String title, final Set<OptionDescription> configurableOptions, String path) {
    final Set<String> words = SearchableOptionsRegistrar.getInstance().getProcessedWordsWithoutStemming(title);
    @NonNls final String regex = "[\\W&&[^\\p{Punct}\\p{Blank}]]";
    for (String option : words) {
      configurableOptions.add(new OptionDescription(option, HTML_PATTERN.matcher(title).replaceAll(" ").replaceAll(regex, " "), path));
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

  public static int getSelection(String tabIdx, final JTabbedPane tabbedPane) {
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
    if (option == null || option.trim().length() == 0) return false;
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
    } else if (rootComponent instanceof JTabbedPane){
      final JTabbedPane tabbedPane = (JTabbedPane)rootComponent;
      final String path = SearchableOptionsRegistrarImpl.getInstance().getInnerPath(configurable, option);
      if (path != null) {
        final int index = SearchUtil.getSelection(path, tabbedPane);
        if (index > - 1 && index < tabbedPane.getTabCount()) {
          tabbedPane.setSelectedIndex(index);
        }
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
    final SearchableOptionsRegistrar registrar = SearchableOptionsRegistrar.getInstance();
    final Set<String> options = registrar.getProcessedWords(filter);
    final Set<String> words = registrar.getProcessedWords(textToMarkup);
    for (String option : options) {
      if (words.contains(option)) {
        final String[] splittedText = textToMarkup.split(option);
        if (splittedText != null && splittedText.length > 0) {
          boolean endsWith = textToMarkup.endsWith(option);
          textToMarkup = "";
          for (int i = 0; i < splittedText.length; i++) {
            String aPart = splittedText[i];
            if (aPart == null || aPart.length() == 0) {
              continue;
            }
            if (!endsWith && i == splittedText.length - 1){
              textToMarkup += aPart;
            } else if (insideHtmlTagPattern.matcher(aPart).matches()) {
              textToMarkup += aPart + option;
            }
            else {
              textToMarkup += aPart + "<font color='#ffffff' bgColor='#1d5da7'>" + option + "</font>";
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


  public static void showHintPopup(final MyTextField searchField,
                                   final SearchableOptionsRegistrar optionsRegistrar,
                                   final JBPopup [] activePopup,
                                   final Alarm showHintAlarm,
                                   final Project project){
    if (activePopup[0] != null) {
      activePopup[0].cancel();
    }
    final String filter = searchField.getText();
    if (filter == null || filter.length() == 0) return;
    final Map<String, Set<String>> hints = optionsRegistrar.findPossibleExtension(filter, project);
    final DefaultListModel model = new DefaultListModel();
    final JList list = new JList(model);
    for (String groupName : hints.keySet()) {
      model.addElement(groupName);
      final Set<String> descriptions = hints.get(groupName);
      if (descriptions != null) {
        for (String hit : descriptions) {
          if (hit == null) continue;
          model.addElement(new OptionDescription(hit));
        }
      }
    }
    list.setCellRenderer(new DefaultListCellRenderer(){
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        final Component rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof String){
          setText("------ " + value + " ------");
        } else if (value instanceof OptionDescription){
          setText(((OptionDescription)value).getHit());
        }
        return rendererComponent;
      }
    });

    if (hints.size() > 0) {
      final Runnable onChosen = new Runnable() {
        public void run() {
          final Object selectedValue = list.getSelectedValue();
          if (selectedValue instanceof OptionDescription) {
            searchField.setText(((OptionDescription)selectedValue).getHit());
            SwingUtilities.invokeLater(new Runnable(){
              public void run() {     //do not show look up again
                showHintAlarm.cancelAllRequests();
              }
            });
          }
        }
      };
      final JBPopup popup = JBPopupFactory.getInstance()
        .createListPopupBuilder(list)
        .setItemChoosenCallback(onChosen)
        .setRequestFocus(false).createPopup();
      popup.showUnderneathOf(searchField);
      activePopup[0] = popup;
      searchField.registerKeyboardAction(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          showPopupWithFocus(popup, list, onChosen, searchField, activePopup, true);
        }
      }, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

      searchField.registerKeyboardAction(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          showPopupWithFocus(popup, list, onChosen, searchField, activePopup, false);
        }
      }, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

      SwingUtilities.invokeLater(new Runnable(){
        public void run() {
          searchField.requestFocusInWindow();
        }
      });
    }
  }

  private static void showPopupWithFocus(final JBPopup popup,
                                         final JList list,
                                         final Runnable onChosen,
                                         final MyTextField searchField,
                                         final JBPopup[] activePopup,
                                         final boolean down) {
    if (popup != null) {
      popup.cancel();
    }
    final JBPopup popupWithFocus = JBPopupFactory.getInstance()
      .createListPopupBuilder(list)
      .setItemChoosenCallback(onChosen)
      .setRequestFocus(true)
      .createPopup();
    if (down) {
      if (list.getSelectedIndex() < list.getModel().getSize() - 1) {
        list.setSelectedIndex(list.getSelectedIndex() + 1);
      }
    } else {
      if (list.getSelectedIndex() > 0){
        list.setSelectedIndex(list.getSelectedIndex() - 1);
      }
    }
    list.addKeyListener(new KeyListener() {
      public void keyTyped(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.VK_UP ||
            e.getKeyCode() != KeyEvent.VK_DOWN ||
            e.getKeyCode() != KeyEvent.VK_PAGE_UP ||
            e.getKeyCode() != KeyEvent.VK_PAGE_DOWN ) {
          if (popupWithFocus != null) {
            popupWithFocus.cancel();
          }
          searchField.processKeyEvent(e);
        }
      }

      public void keyPressed(KeyEvent e) {
      }

      public void keyReleased(KeyEvent e) {
      }
    });
    popupWithFocus.showUnderneathOf(searchField);
    activePopup[0] = popupWithFocus;
  }


  //make process event public
  public static class MyTextField extends JTextField {

    public void processKeyEvent(KeyEvent e) {
      super.processKeyEvent(e);
    }
  }
}

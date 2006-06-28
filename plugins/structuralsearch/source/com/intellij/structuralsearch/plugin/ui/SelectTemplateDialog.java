package com.intellij.structuralsearch.plugin.ui;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Collection;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Apr 23, 2004
 * Time: 5:03:52 PM
 * To change this template use File | Settings | File Templates.
 */
public class SelectTemplateDialog extends DialogWrapper {
  private boolean showHistory;
  private Editor searchPatternEditor;
  private Editor replacePatternEditor;
  private boolean replace;
  private Project project;
  private final ExistingTemplatesComponent existingTemplatesComponent;

  private MySelectionListener selectionListener;
  private CardLayout myCardLayout;
  private JPanel myPreviewPanel;
  @NonNls private static final String PREVIEW_CARD = "Preview";
  @NonNls private static final String SELECT_TEMPLATE_CARD = "SelectCard";

  public SelectTemplateDialog(Project project, boolean showHistory, boolean replace) {
    super(project, false);

    this.project = project;
    this.showHistory = showHistory;
    this.replace = replace;
    existingTemplatesComponent = ExistingTemplatesComponent.getInstance(this.project);

    setTitle(SSRBundle.message(this.showHistory ? "used.templates.history.dialog.title" : "existing.templates.dialog.title"));

    getOKAction().putValue(Action.MNEMONIC_KEY, new Integer('O'));
    init();

    if (this.showHistory) {
      final int selection = existingTemplatesComponent.getHistoryList().getSelectedIndex();
      if (selection != -1) {
        setPatternFromList(selection);
      }
    }
    else {
      final TreePath selection = existingTemplatesComponent.getPatternTree().getSelectionPath();
      if (selection != null) {
        setPatternFromNode((DefaultMutableTreeNode)selection.getLastPathComponent());
      }
      else {
        showPatternPreviewFromConfiguration(null);
      }
    }

    setupListeners();
  }

  class MySelectionListener implements TreeSelectionListener, ListSelectionListener {
    public void valueChanged(TreeSelectionEvent e) {
      if (e.getNewLeadSelectionPath() != null) {
        setPatternFromNode(
          (DefaultMutableTreeNode)e.getNewLeadSelectionPath().getLastPathComponent()
        );
      }
    }

    public void valueChanged(ListSelectionEvent e) {
      if (e.getValueIsAdjusting() || e.getLastIndex() == -1) return;
      int selectionIndex = existingTemplatesComponent.getHistoryList().getSelectedIndex();
      if (selectionIndex != -1) {
        setPatternFromList(selectionIndex);
      }
    }
  }

  private void setPatternFromList(int index) {
    showPatternPreviewFromConfiguration(
      (Configuration)existingTemplatesComponent.getHistoryList().getModel().getElementAt(index)
    );
  }

  protected JComponent createCenterPanel() {
    final JPanel centerPanel = new JPanel(new BorderLayout());
    Splitter splitter;

    centerPanel.add(BorderLayout.CENTER, splitter = new Splitter(false, 0.3f));
    centerPanel.add(splitter);

    splitter.setFirstComponent(
      showHistory ?
      existingTemplatesComponent.getHistoryPanel() :
      existingTemplatesComponent.getTemplatesPanel()
    );
    final JPanel panel;
    splitter.setSecondComponent(
      panel = new JPanel(new BorderLayout())
    );

    searchPatternEditor = UIUtil.createEditor(
      EditorFactory.getInstance().createDocument(""),
      project,
      false,
      true
    );

    JComponent centerComponent;

    if (replace) {
      replacePatternEditor = UIUtil.createEditor(
        EditorFactory.getInstance().createDocument(""),
        project,
        false,
        true
      );
      centerComponent = new Splitter(true);
      ((Splitter)centerComponent).setFirstComponent(searchPatternEditor.getComponent());
      ((Splitter)centerComponent).setSecondComponent(replacePatternEditor.getComponent());
    }
    else {
      centerComponent = searchPatternEditor.getComponent();
    }

    myCardLayout = new CardLayout();
    myPreviewPanel = new JPanel(myCardLayout);
    myPreviewPanel.add(centerComponent, PREVIEW_CARD);
    JPanel selectPanel = new JPanel(new GridBagLayout());
    GridBagConstraints gb = new GridBagConstraints(0,0,0,0,0,0,GridBagConstraints.CENTER,GridBagConstraints.NONE, new Insets(0,0,0,0),0,0);
    selectPanel.add(new JLabel(SSRBundle.message("selecttemplate.template.label.please.select.template")), gb);
    myPreviewPanel.add(selectPanel, SELECT_TEMPLATE_CARD);

    panel.add(BorderLayout.CENTER, myPreviewPanel);

    panel.add(BorderLayout.NORTH, new JLabel(SSRBundle.message("selecttemplate.template.preview")));
    return centerPanel;
  }

  public void dispose() {
    EditorFactory.getInstance().releaseEditor(searchPatternEditor);
    if (replacePatternEditor != null) EditorFactory.getInstance().releaseEditor(replacePatternEditor);
    removeListeners();
    super.dispose();
  }

  public JComponent getPreferredFocusedComponent() {
    return showHistory ?
           existingTemplatesComponent.getHistoryList() :
           existingTemplatesComponent.getPatternTree();
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.structuralsearch.plugin.ui.SelectTemplateDialog";
  }

  private void setupListeners() {
    existingTemplatesComponent.setOwner(this);
    selectionListener = new MySelectionListener();

    if (showHistory) {
      existingTemplatesComponent.getHistoryList().getSelectionModel().addListSelectionListener(
        selectionListener
      );
    }
    else {
      existingTemplatesComponent.getPatternTree().getSelectionModel().addTreeSelectionListener(
        selectionListener
      );
    }
  }

  private void removeListeners() {
    existingTemplatesComponent.setOwner(null);
    if (showHistory) {
      existingTemplatesComponent.getHistoryList().getSelectionModel().removeListSelectionListener(
        selectionListener
      );
    }
    else {
      existingTemplatesComponent.getPatternTree().getSelectionModel().removeTreeSelectionListener(selectionListener);
    }
  }

  private void setPatternFromNode(DefaultMutableTreeNode node) {
    if (node == null) return;
    final Object userObject = node.getUserObject();
    final Configuration configuration;

    // root could be without search template
    if (userObject instanceof PredefinedConfiguration) {
      final PredefinedConfiguration config = (PredefinedConfiguration)userObject;
      configuration = config.getConfiguration();
    }
    else if (userObject instanceof Configuration) {
      configuration = (Configuration)userObject;
    }
    else {
      configuration = null;
    }

    showPatternPreviewFromConfiguration(configuration);
  }

  private void showPatternPreviewFromConfiguration(@Nullable final Configuration configuration) {
    if (configuration == null) {
      myCardLayout.show(myPreviewPanel, SELECT_TEMPLATE_CARD);
      return;
    }
    else {
      myCardLayout.show(myPreviewPanel, PREVIEW_CARD);
    }
    final MatchOptions matchOptions = configuration.getMatchOptions();

    UIUtil.setContent(
      searchPatternEditor,
      matchOptions.getSearchPattern(),
      0,
      searchPatternEditor.getDocument().getTextLength(),
      project
    );

    searchPatternEditor.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, configuration);

    if (replace) {
      String replacement;

      if (configuration instanceof ReplaceConfiguration) {
        replacement = ((ReplaceConfiguration)configuration).getOptions().getReplacement();
      }
      else {
        replacement = configuration.getMatchOptions().getSearchPattern();
      }

      UIUtil.setContent(
        replacePatternEditor,
        replacement,
        0,
        replacePatternEditor.getDocument().getTextLength(),
        project
      );

      replacePatternEditor.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, configuration);
    }
  }

  @NotNull public Configuration[] getSelectedConfigurations() {
    if (showHistory) {
      Object[] selectedValues = existingTemplatesComponent.getHistoryList().getSelectedValues();
      if (selectedValues == null) {
        return new Configuration[0];
      }
      Collection<Configuration> configurations = new ArrayList<Configuration>();
      for (Object selectedValue : selectedValues) {
        if (selectedValue instanceof Configuration) {
          configurations.add((Configuration)selectedValue);
        }
      }
      return configurations.toArray(new Configuration[configurations.size()]);
    }
    else {
      TreePath[] paths = existingTemplatesComponent.getPatternTree().getSelectionModel().getSelectionPaths();
      if (paths == null) {
        return new Configuration[0];
      }
      Collection<Configuration> configurations = new ArrayList<Configuration>();
      for (TreePath path : paths) {

        DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
        final Object userObject = node.getUserObject();
        if (userObject instanceof Configuration) {
          configurations.add((Configuration)userObject);
        }
      }
      return configurations.toArray(new Configuration[configurations.size()]);
    }
  }
}

/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.find.findUsages;

import com.intellij.find.FindBundle;
import com.intellij.find.FindSettings;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.StateRestoringCheckBox;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author peter
 */
public abstract class AbstractFindUsagesDialog extends DialogWrapper {
  protected final Project myProject;
  protected final FindUsagesOptions myFindUsagesOptions;

  private final boolean myToShowInNewTab;
  private final boolean myIsShowInNewTabEnabled;
  protected final boolean myIsShowInNewTabVisible;

  private final boolean mySearchForTextOccurencesAvailable;

  private final boolean mySearchInLibrariesAvailable;

  private JCheckBox myCbToOpenInNewTab;

  protected StateRestoringCheckBox myCbToSearchForTextOccurences;
  protected JCheckBox myCbToSkipResultsWhenOneUsage;

  private ActionListener myUpdateAction;

  private ScopeChooserCombo myScopeCombo;

  protected AbstractFindUsagesDialog(Project project,
                                     FindUsagesOptions findUsagesOptions,
                                     boolean toShowInNewTab,
                                     boolean mustOpenInNewTab,
                                     boolean isSingleFile,
                                     boolean searchForTextOccurencesAvailable,
                                     boolean searchInLibrariesAvailable) {
    super(project, true);
    myProject = project;
    myFindUsagesOptions = findUsagesOptions;
    myToShowInNewTab = toShowInNewTab;
    myIsShowInNewTabEnabled = !mustOpenInNewTab;
    myIsShowInNewTabVisible = !isSingleFile;
    mySearchForTextOccurencesAvailable = searchForTextOccurencesAvailable;
    mySearchInLibrariesAvailable = searchInLibrariesAvailable;

    myUpdateAction = new ActionListener() {
      public void actionPerformed(ActionEvent event) {
        update();
      }
    };

    setButtonsMargin(null);

    setOKButtonText(FindBundle.message("find.dialog.find.button"));
    setOKButtonIcon(IconLoader.getIcon("/actions/find.png"));
    setTitle(isSingleFile ? FindBundle.message("find.usages.in.file.dialog.title") : FindBundle.message("find.usages.dialog.title"));

  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected boolean isInFileOnly() {
    return !myIsShowInNewTabVisible;
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = new Insets(4, 4, 4, 4);
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 1;
    gbConstraints.anchor = GridBagConstraints.EAST;
    final JLabel promptLabel = new JLabel(getLabelText());
    panel.add(promptLabel, gbConstraints);

    return panel;
  }

  public abstract String getLabelText();

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    JPanel _panel = new JPanel(new BorderLayout());
    _panel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
    panel.add(_panel, new GridBagConstraints(0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
                                             new Insets(0, 0, 0, 0), 0, 0));

    if (myIsShowInNewTabVisible) {
      myCbToOpenInNewTab = new JCheckBox(FindBundle.message("find.open.in.new.tab.checkbox"));
      myCbToOpenInNewTab.setSelected(myToShowInNewTab);
      myCbToOpenInNewTab.setEnabled(myIsShowInNewTabEnabled);
      _panel.add(myCbToOpenInNewTab, BorderLayout.EAST);
    }

    JPanel allOptionsPanel = createAllOptionsPanel();
    if (allOptionsPanel != null) {
      panel.add(allOptionsPanel, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                                        new Insets(0, 0, 0, 0), 0, 0));
    }
    return panel;
  }

  public final FindUsagesOptions calcFindUsagesOptions() {
    calcFindUsagesOptions(myFindUsagesOptions);
    return myFindUsagesOptions;
  }

  protected void init() {
    super.init();
    update();
  }

  public void calcFindUsagesOptions(FindUsagesOptions options) {
    if (myScopeCombo != null) {
      options.searchScope = myScopeCombo.getSelectedScope();
    }
    else {
      options.searchScope = GlobalSearchScope.allScope(myProject);
    }

    options.isSearchForTextOccurences = isToChange(myCbToSearchForTextOccurences) && isSelected(myCbToSearchForTextOccurences);
  }

  protected void update() {
  }

  public boolean isShowInSeparateWindow() {
    return myCbToOpenInNewTab != null && myCbToOpenInNewTab.isSelected();
  }

  public boolean isSkipResultsWhenOneUsage() {
    return myCbToSkipResultsWhenOneUsage != null && myCbToSkipResultsWhenOneUsage.isSelected();
  }

  protected void doOKAction() {
    if (!shouldDoOkAction()) return;

    FindSettings settings = FindSettings.getInstance();

    if (myScopeCombo != null) {
      settings.setDefaultScopeName(myScopeCombo.getSelectedScopeName());
    }
    if (mySearchForTextOccurencesAvailable && myCbToSearchForTextOccurences != null && myCbToSearchForTextOccurences.isEnabled()) {
      settings.setSearchForTextOccurences(myCbToSearchForTextOccurences.isSelected());
    }

    if (myCbToSkipResultsWhenOneUsage != null) {
      settings.setSkipResultsWithOneUsage(isSkipResultsWhenOneUsage());
    }

    super.doOKAction();
  }

  protected boolean shouldDoOkAction() {
    return myScopeCombo == null || myScopeCombo.getSelectedScope() != null;
  }

  protected static boolean isToChange(JCheckBox cb) {
    return cb != null && cb.getParent() != null;
  }

  protected static boolean isSelected(JCheckBox cb) {
    return cb != null && cb.getParent() != null && cb.isSelected();
  }

  protected StateRestoringCheckBox addCheckboxToPanel(String name, boolean toSelect, JPanel panel, boolean toUpdate) {
    StateRestoringCheckBox cb = new StateRestoringCheckBox(name);
    cb.setSelected(toSelect);
    panel.add(cb);
    if (toUpdate) {
      cb.addActionListener(myUpdateAction);
    }
    return cb;
  }

  protected JPanel createAllOptionsPanel() {
    JPanel allOptionsPanel = new JPanel();

    JPanel findWhatPanel = createFindWhatPanel();
    JPanel usagesOptionsPanel = createUsagesOptionsPanel();
    int grids = 0;
    if (findWhatPanel != null) {
      grids++;
    }
    if (usagesOptionsPanel != null) {
      grids++;
    }
    if (grids != 0) {
      allOptionsPanel.setLayout(new GridLayout(1, grids, 8, 0));
      if (findWhatPanel != null) {
        allOptionsPanel.add(findWhatPanel);
      }
      if (usagesOptionsPanel != null) {
        allOptionsPanel.add(usagesOptionsPanel);
      }
    }

    JComponent scopePanel = createSearchScopePanel();
    if (scopePanel != null) {
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(allOptionsPanel, BorderLayout.CENTER);
      panel.add(scopePanel, BorderLayout.SOUTH);
      return panel;
    }

    return allOptionsPanel;
  }

  @Nullable
  protected abstract JPanel createFindWhatPanel();

  protected void addUsagesOptions(JPanel optionsPanel) {
    if (mySearchForTextOccurencesAvailable) {
      myCbToSearchForTextOccurences = addCheckboxToPanel(FindBundle.message("find.options.search.for.text.occurences.checkbox"),
                                                         FindSettings.getInstance().isSearchForTextOccurences(), optionsPanel, false);

    }

    if (myIsShowInNewTabVisible) {
      myCbToSkipResultsWhenOneUsage = addCheckboxToPanel(FindBundle.message("find.options.skip.results.tab.with.one.usage.checkbox"),
                                                         FindSettings.getInstance().isSkipResultsWithOneUsage(), optionsPanel, false);

    }
  }

  @Nullable
  protected JPanel createUsagesOptionsPanel() {
    JPanel optionsPanel = new JPanel();
    optionsPanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.options.group")));
    optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
    addUsagesOptions(optionsPanel);
    return optionsPanel.getComponents().length == 0 ? null : optionsPanel;
  }

  @Nullable
  protected JComponent createSearchScopePanel() {
    if (isInFileOnly()) return null;
    JPanel optionsPanel = new JPanel(new BorderLayout());
    JLabel label = new JLabel(FindBundle.message("find.scope.label"));
    optionsPanel.add(label, BorderLayout.WEST);
    myScopeCombo = new ScopeChooserCombo(myProject, mySearchInLibrariesAvailable, true, FindSettings.getInstance().getDefaultScopeName());
    optionsPanel.add(myScopeCombo, BorderLayout.CENTER);
    label.setLabelFor(myScopeCombo.getComboBox());
    return optionsPanel;
  }

  public final SearchScope getScope() {
    return myScopeCombo.getSelectedScope();
  }

  @Nullable
  protected JComponent getPreferredFocusedControl() {
    return null;
  }

  public JComponent getPreferredFocusedComponent() {
    if (myScopeCombo != null) {
      return myScopeCombo.getComboBox();
    }
    return getPreferredFocusedControl();
  }


}

package com.intellij.analysis;

import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.UIOptions;
import com.intellij.find.FindSettings;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.SearchScope;
import com.intellij.ui.IdeBorderFactory;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Enumeration;
import java.util.HashSet;

/**
 * User: anna
 * Date: Jul 6, 2005
 */
public class BaseAnalysisActionDialog extends DialogWrapper {
  private String myFileName;
  private String myModuleName;
  private JRadioButton myProjectButton;
  private JRadioButton myModuleButton;
  private JRadioButton myUncommitedFiles;
  private JRadioButton myCustomScopeButton;
  private ScopeChooserCombo myScopeCombo;
  private JCheckBox myInspectTestSource;
  private Project myProject;
  private boolean myRememberScope;
  private String myAnalysisNoon;

  public BaseAnalysisActionDialog(String title,
                                  String analysisNoon,
                                  Project project,
                                  String fileName,
                                  String moduleName,
                                  boolean rememberScope) {
    super(true);
    myProject = project;
    myFileName = fileName;
    myModuleName = moduleName;
    myRememberScope = rememberScope;
    myAnalysisNoon = analysisNoon;
    init();
    setTitle(title);
  }

  public void setOKActionEnabled(boolean isEnabled) {
    super.setOKActionEnabled(isEnabled);
  }

  protected JComponent createCenterPanel() {
    final UIOptions uiOptions = ((InspectionManagerEx)InspectionManagerEx.getInstance(myProject)).getUIOptions();

    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gc = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0,0,0,0), 0, 0);
    panel.setBorder(IdeBorderFactory.createTitledBorder(myAnalysisNoon));

      //include test option
    myInspectTestSource = new JCheckBox(AnalysisScopeBundle.message("scope.option.include.test.sources"), uiOptions.ANALYZE_TEST_SOURCES);
    gc.anchor = GridBagConstraints.EAST;
    panel.add(myInspectTestSource, gc);

    ButtonGroup group = new ButtonGroup();

    //project scope
    myProjectButton = new JRadioButton(AnalysisScopeBundle.message("scope.option.whole.project"));
    group.add(myProjectButton);
    gc.anchor = GridBagConstraints.WEST;
    panel.add(myProjectButton, gc);

    //module scope if applicable
    boolean useModuleScope = false;
    if (myModuleName != null) {
      myModuleButton = new JRadioButton(AnalysisScopeBundle.message("scope.option.module.with.mnemonic", myModuleName));
      group.add(myModuleButton);
      useModuleScope = uiOptions.SCOPE_TYPE == AnalysisScope.MODULE;
      myModuleButton.setSelected(myRememberScope && useModuleScope);
      panel.add(myModuleButton, gc);
    }

    boolean useUncommitedFiles = false;
    if (ChangeListManager.getInstance(myProject).getAffectedFiles().size() > 0){
      myUncommitedFiles = new JRadioButton(AnalysisScopeBundle.message("scope.option.uncommited.files"));
      group.add(myUncommitedFiles);
      useUncommitedFiles = uiOptions.SCOPE_TYPE == AnalysisScope.UNCOMMITED_FILES;
      myUncommitedFiles.setSelected(myRememberScope && useUncommitedFiles);
      panel.add(myUncommitedFiles, gc);
    }

    //file/package/directory/module scope
    final JRadioButton fileButton = new JRadioButton(myFileName);
    fileButton.setMnemonic(myFileName.charAt(0));
    group.add(fileButton);
    panel.add(fileButton, gc);

    //custom scope
    myCustomScopeButton = new JRadioButton(AnalysisScopeBundle.message("scope.option.custom"));
    myCustomScopeButton.setSelected(myRememberScope && uiOptions.SCOPE_TYPE == AnalysisScope.CUSTOM);
    group.add(myCustomScopeButton);
    gc.gridwidth = 1;
    panel.add(myCustomScopeButton, gc);

    myScopeCombo = new ScopeChooserCombo(myProject, false, true, uiOptions.CUSTOM_SCOPE_NAME.length() > 0 ? uiOptions.CUSTOM_SCOPE_NAME : FindSettings.getInstance().getDefaultScopeName());
    gc.gridx = 1;
    panel.add(myScopeCombo, gc);
    gc.gridx = 0;
    gc.gridwidth = 2;



    //correct selection
    myProjectButton.setSelected(myRememberScope && uiOptions.SCOPE_TYPE == AnalysisScope.PROJECT);
    fileButton.setSelected(!myRememberScope || (uiOptions.SCOPE_TYPE != AnalysisScope.PROJECT && !useModuleScope && uiOptions.SCOPE_TYPE != AnalysisScope.CUSTOM && !useUncommitedFiles));

    myScopeCombo.setEnabled(myCustomScopeButton.isSelected());
    final ActionListener customScopeUpdateAction = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myScopeCombo.setEnabled(myCustomScopeButton.isSelected());
      }
    };
    final Enumeration<AbstractButton> enumeration = group.getElements();
    while (enumeration.hasMoreElements()) {
      enumeration.nextElement().addActionListener(customScopeUpdateAction);
    }

    //additional panel - inspection profile chooser
    JPanel wholePanel = new JPanel(new BorderLayout());
    wholePanel.add(panel, BorderLayout.NORTH);
    final JComponent additionalPanel = getAdditionalActionSettings(myProject);
    if (additionalPanel!= null){
      wholePanel.add(additionalPanel, BorderLayout.CENTER);
    }
    return wholePanel;
  }

  @Nullable
  protected JComponent getAdditionalActionSettings(final Project project) {
    return null;
  }

  public boolean isProjectScopeSelected() {
    return myProjectButton.isSelected();
  }

  public boolean isModuleScopeSelected() {
    return myModuleButton != null && myModuleButton.isSelected();
  }

  public boolean isUncommitedFilesSelected(){
    return myUncommitedFiles != null && myUncommitedFiles.isSelected();
  }

  public SearchScope getCustomScope(){
    if (myCustomScopeButton.isSelected()){
      return myScopeCombo.getSelectedScope();
    }
    return null;
  }

  protected void doOKAction() {
    ((InspectionManagerEx)InspectionManagerEx.getInstance(myProject)).getUIOptions().CUSTOM_SCOPE_NAME = myScopeCombo.getSelectedScopeName();
    super.doOKAction();
  }

  public boolean isInspectTestSources(){
    return myInspectTestSource.isSelected();
  }

  public AnalysisScope getScope(UIOptions uiOptions, AnalysisScope scope, Project project, Module module){
    if (isProjectScopeSelected()) {
      scope = new AnalysisScope(project);
      uiOptions.SCOPE_TYPE = AnalysisScope.PROJECT;
    }
    else {
      final SearchScope customScope = getCustomScope();
      if (customScope != null){
        scope = new AnalysisScope(customScope, project);
        uiOptions.SCOPE_TYPE = AnalysisScope.CUSTOM;
        uiOptions.CUSTOM_SCOPE_NAME = customScope.getDisplayName();
      } else if (isModuleScopeSelected()) {
        scope = new AnalysisScope(module);
        uiOptions.SCOPE_TYPE = AnalysisScope.MODULE;
      } else if (isUncommitedFilesSelected()) {
        scope = new AnalysisScope(project, new HashSet<VirtualFile>(ChangeListManager.getInstance(project).getAffectedFiles()));
        uiOptions.SCOPE_TYPE = AnalysisScope.UNCOMMITED_FILES;
      } else {
        uiOptions.SCOPE_TYPE = AnalysisScope.FILE;//just not project scope
      }
    }
    uiOptions.ANALYZE_TEST_SOURCES = isInspectTestSources();
    scope.setIncludeTestSource(isInspectTestSources());
    return scope;
  }
}

package com.intellij.analysis;

import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.IdeBorderFactory;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * User: anna
 * Date: Jul 6, 2005
 */
public class BaseAnalysisActionDialog extends DialogWrapper {
  private String myFileName;
  private String myModuleName;
  private JRadioButton myFileButton;
  private JRadioButton myProjectButton;
  private JRadioButton myModuleButton;
  private JCheckBox myInspectTestSource;
  private Project myProject;
  private boolean myRememberScope;
  private String myAnalysisNoon;

  public BaseAnalysisActionDialog(String title,
                                  String analysisNoon,
                                  Project project,
                                  String fileName,
                                  String moduleName,
                                  boolean isProjectScope,
                                  boolean rememberScope) {
    super(true);
    myProject = project;
    myFileName = fileName;
    myModuleName = moduleName;
    myRememberScope = rememberScope;
    myAnalysisNoon = analysisNoon;
    init();
    setTitle(title);
    if (isProjectScope){
      myFileButton.setVisible(false);
      myProjectButton.setSelected(true);
    }
  }

  public void setOKActionEnabled(boolean isEnabled) {
    super.setOKActionEnabled(isEnabled);
  }

  protected JComponent createCenterPanel() {
    final InspectionManagerEx.UIOptions uiOptions = ((InspectionManagerEx)InspectionManagerEx.getInstance(myProject)).getUIOptions();
    JPanel wholePanel = new JPanel(new BorderLayout());
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gc = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0,0,0,0), 0, 0);
    panel.setBorder(IdeBorderFactory.createTitledBorder(myAnalysisNoon));
    myFileButton = new JRadioButton(myFileName);
    myFileButton.setMnemonic(myFileName.charAt(0));
    myProjectButton = new JRadioButton("Whole project");
    myProjectButton.setMnemonic(KeyEvent.VK_W);
    ButtonGroup group = new ButtonGroup();
    group.add(myProjectButton);
    group.add(myFileButton);
    panel.add(myProjectButton, gc);
    boolean useModuleScope = false;
    if (myModuleName != null) {
      myModuleButton = new JRadioButton("Module \'" + myModuleName + "\'");
      myModuleButton.setMnemonic(KeyEvent.VK_M);
      group.add(myModuleButton);
      useModuleScope = uiOptions.SCOPE_TYPE == AnalysisScope.MODULE;
      myModuleButton.setSelected(myRememberScope && useModuleScope);
      panel.add(myModuleButton, gc);
    }
    panel.add(myFileButton, gc);
    myInspectTestSource = new JCheckBox("Include Test Sources", uiOptions.ANALYZE_TEST_SOURCES);
    gc.insets.left = 15;
    panel.add(myInspectTestSource, gc);
    wholePanel.add(panel, BorderLayout.NORTH);
    final JComponent additionalPanel = getAdditionalActionSettings(myProject);
    if (additionalPanel!= null){
      wholePanel.add(additionalPanel, BorderLayout.CENTER);
    }
    myProjectButton.setSelected(myRememberScope && uiOptions.SCOPE_TYPE == AnalysisScope.PROJECT);
    myFileButton.setSelected(!myRememberScope || (uiOptions.SCOPE_TYPE != AnalysisScope.PROJECT && !useModuleScope));
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
    return myModuleButton != null ? myModuleButton.isSelected() : false;
  }

  public boolean isInspectTestSources(){
    return myInspectTestSource.isSelected();
  }
}

package com.intellij.codeInspection.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileManager;
import com.intellij.codeInspection.ui.InspectCodePanel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.CommonBundle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CodeInspectionAction extends BaseAnalysisAction {
  public CodeInspectionAction() {
    super(InspectionsBundle.message("inspection.action.title"), InspectionsBundle.message("inspection.action.noun"));
  }

  protected void analyze(Project project, AnalysisScope scope) {
    FileDocumentManager.getInstance().saveAllDocuments();
    final InspectionManagerEx inspectionManagerEx = ((InspectionManagerEx)InspectionManager.getInstance(project));
    inspectionManagerEx.setCurrentScope(scope);
    inspectionManagerEx.doInspections(scope);
  }

  protected JComponent getAdditionalActionSettings(final Project project, final BaseAnalysisActionDialog dialog) {
    final InspectionProfileManager inspectionManager = InspectionProfileManager.getInstance();
    final InspectionManagerEx manager = (InspectionManagerEx)InspectionManager.getInstance(project);
    LabeledComponent component = new LabeledComponent();
    component.setText(InspectionsBundle.message("inspection.action.profile.label"));
    component.setLabelLocation(BorderLayout.WEST);
    ComboboxWithBrowseButton comboboxWithBrowseButton = new ComboboxWithBrowseButton();
    component.setComponent(comboboxWithBrowseButton);
    final JComboBox profiles = comboboxWithBrowseButton.getComboBox();
    reloadProfiles(profiles, inspectionManager, manager);
    comboboxWithBrowseButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        InspectCodePanel inspectCodeDialog = new InspectCodePanel(manager, null, (String)profiles.getSelectedItem()){
          protected void init() {
            super.init();
            setOKButtonText(CommonBundle.getOkButtonText());
          }
        };
        inspectCodeDialog.show();
        if (inspectCodeDialog.isOK()){
          reloadProfiles(profiles, inspectionManager, manager);
        } else {
          //if profile was disabled and cancel after apply was pressed
          final InspectionProfileImpl profile = inspectionManager.getProfile((String)profiles.getSelectedItem());
          final boolean canExecute = profile != null && profile.isExecutable();
          dialog.setOKActionEnabled(canExecute);
        }
      }
    });
    profiles.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final InspectionProfileImpl profile = inspectionManager.getProfile((String)profiles.getSelectedItem());
        final boolean canExecute = profile != null && profile.isExecutable();
        dialog.setOKActionEnabled(canExecute);
        if (canExecute){
          manager.setProfile(profile);
        }
      }
    });
    final InspectionProfileImpl profile = inspectionManager.getProfile((String)profiles.getSelectedItem());
    dialog.setOKActionEnabled(profile != null && profile.isExecutable());
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(component, BorderLayout.NORTH);
    return panel;
  }

  private void reloadProfiles(JComboBox profiles, InspectionProfileManager inspectionProfilesManager, InspectionManagerEx inspectionManager){
    final String selectedProfile = inspectionManager.getCurrentProfile().getName();
    final String[] avaliableProfileNames = inspectionProfilesManager.getAvaliableProfileNames();
    final DefaultComboBoxModel model = (DefaultComboBoxModel)profiles.getModel();
    model.removeAllElements();
    for (String profile : avaliableProfileNames) {
      model.addElement(profile);
    }
    profiles.setSelectedItem(selectedProfile);
  }
}

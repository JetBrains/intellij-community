package com.intellij.application.options;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.ex.Descriptor;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.ex.InspectionToolsPanel;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.packageDependencies.ui.DependencyConfigurable;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ErrorHighlightingPanel extends InspectionToolsPanel {

  private JTextField myAutoreparseDelayField;
  private FieldPanel myAdditionalTagsField;
  private JCheckBox myCbShowImportPopup;

  public ErrorHighlightingPanel() {
    super(DaemonCodeAnalyzerSettings.getInstance().getInspectionProfile().getName(), null);
    add(createAutoreparsePanel(), BorderLayout.NORTH);
  }

  protected InspectionTool[] getTools() {
    return mySelectedProfile.getLocalInspectionToolWrappers();
  }

  protected void initDescriptors() {
    super.initDescriptors();
    myDescriptors.add(createDescriptor("Deprecated symbol", HighlightDisplayKey.DEPRECATED_SYMBOL, null, "Local_DeprecatedSymbol.html"));
    myDescriptors.add(createDescriptor("Unused import", HighlightDisplayKey.UNUSED_IMPORT, null, "Local_UnusedImport.html"));
    myDescriptors.add(createDescriptor("Unused symbol", HighlightDisplayKey.UNUSED_SYMBOL, null, "Local_UnusedSymbol.html"));
    myDescriptors.add(
      createDescriptor("Unused throws declaration", HighlightDisplayKey.UNUSED_THROWS_DECL, null, "Local_UnusedThrowsDeclaration.html"));
    myDescriptors.add(createDescriptor("Silly assignment", HighlightDisplayKey.SILLY_ASSIGNMENT, null, "Local_SillyAssignment.html"));
    myDescriptors.add(createDescriptor("Access static member via instance reference", HighlightDisplayKey.ACCESS_STATIC_VIA_INSTANCE,
                                       null, "Local_StaticViaInstance.html"));
    myDescriptors.add(
      createDescriptor("Wrong package statement", HighlightDisplayKey.WRONG_PACKAGE_STATEMENT, null, "Local_WrongPackage.html"));

    myDescriptors.add(createDescriptor("Illegal package dependencies", HighlightDisplayKey.ILLEGAL_DEPENDENCY, createDependencyConigurationPanel(),
                                       "Local_IllegalDependencies.html"));
    myDescriptors.add(createDescriptor("JavaDoc errors", HighlightDisplayKey.JAVADOC_ERROR, null, "Local_JavaDoc.html"));

    myAdditionalTagsField = new FieldPanel("Additional JavaDoc Tags", "Edit Additional JavaDoc Tags", null, null);
    myAdditionalTagsField.setPreferredSize(new Dimension(150, myAdditionalTagsField.getPreferredSize().height));
    myAdditionalTagsField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        if (mySelectedProfile != null) {
          mySelectedProfile.setAdditionalJavadocTags(myAdditionalTagsField.getText().trim());
        }
      }
    });
    if (mySelectedProfile != null) {
      myAdditionalTagsField.setText(mySelectedProfile.getAdditionalJavadocTags());
    }
    myDescriptors.add(createDescriptor("Unknown javadoc tags", HighlightDisplayKey.UNKNOWN_JAVADOC_TAG, myAdditionalTagsField,
                                       "Local_UnknownJavaDocTags.html"));

    myDescriptors.add(createDescriptor("EJB errors", HighlightDisplayKey.EJB_ERROR, null, "Local_EJBErrors.html"));
    myDescriptors.add(createDescriptor("EJB warnings", HighlightDisplayKey.EJB_WARNING, null, "Local_EJBWarnings.html"));

  }

  private Descriptor createDescriptor(String displayName, HighlightDisplayKey key, JComponent optionsPanel, String decriptionFileName) {
    return new Descriptor(displayName, key, optionsPanel, decriptionFileName, mySelectedProfile.getErrorLevel(key),
                          mySelectedProfile.isToolEnabled(key));
  }


  private JPanel createDependencyConigurationPanel() {
    final JButton editDependencies = new JButton("Configure dependency rules");
    editDependencies.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Project project = (Project)DataManager.getInstance().getDataContext(editDependencies).getData(DataConstants.PROJECT);
        if (project == null) project = ProjectManager.getInstance().getDefaultProject();
        ShowSettingsUtil.getInstance().editConfigurable(editDependencies, new DependencyConfigurable(project));
      }
    });

    JPanel depPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
    depPanel.add(editDependencies);
    return depPanel;
  }

  private JPanel createAutoreparsePanel() {
    JPanel panel1 = new JPanel();
    panel1.setBorder(IdeBorderFactory.createTitledBorder("Autoreparse"));
    JPanel panel = panel1;
    panel.setLayout(new GridBagLayout());
    panel.add(new JLabel("Autoreparse delay (ms)      "),
              new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                     new Insets(5, 5, 5, 5), 0, 0));
    myAutoreparseDelayField = new JTextField(4);
    panel.add(myAutoreparseDelayField,
              new GridBagConstraints(1, 0, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.NONE,
                                     new Insets(5, 5, 5, 5), 0, 0));
    myCbShowImportPopup = new JCheckBox("Show import popup");
    panel.add(myCbShowImportPopup,
              new GridBagConstraints(0, 1, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE,
                                     new Insets(5, 5, 5, 5), 0, 0));
    return panel;
  }

  public void reset() {
    super.reset();
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    myAutoreparseDelayField.setText(Integer.toString(settings.AUTOREPARSE_DELAY));

    myCbShowImportPopup.setSelected(settings.isImportHintEnabled());

  }

  public void apply() throws ConfigurationException {
    //mySelectedProfile.setAdditionalJavadocTags(myAdditionalTagsField.getText());
    super.apply();
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    settings.setInspectionProfile((InspectionProfileImpl)mySelectedProfile.getParentProfile());

    settings.AUTOREPARSE_DELAY = getAutoReparseDelay();
    settings.setImportHintEnabled(myCbShowImportPopup.isSelected());

    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (int i = 0; i < projects.length; i++) {
      DaemonCodeAnalyzer.getInstance(projects[i]).settingsChanged();
    }

  }

  public boolean isModified() {
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    boolean isModified = false;
    isModified |= settings.AUTOREPARSE_DELAY != getAutoReparseDelay();
    isModified |= myCbShowImportPopup.isSelected() != settings.isImportHintEnabled();
    if (isModified) return true;
    return super.isModified();
  }


  private int getAutoReparseDelay() {
    try {
      int delay = Integer.parseInt(myAutoreparseDelayField.getText());
      if (delay < 0) {
        delay = 0;
      }
      return delay;
    }
    catch (NumberFormatException e) {
      return 0;
    }
  }

}

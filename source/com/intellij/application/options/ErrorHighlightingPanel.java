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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.packageDependencies.ui.DependencyConfigurable;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ErrorHighlightingPanel extends InspectionToolsPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.options.ErrorHighlightingPanel");

  private JTextField myAutoreparseDelayField;
  private JCheckBox myCbShowImportPopup;
  private JTextField myMarkMinHeight;
  private JPanel myPanel;

  public ErrorHighlightingPanel() {
    super(DaemonCodeAnalyzerSettings.getInstance().getInspectionProfile().getName(), null);
    add(getAutoreparsePanel(), BorderLayout.NORTH);
  }

  protected InspectionTool[] getTools() {
    return mySelectedProfile.getLocalInspectionToolWrappers();
  }

  protected void initDescriptors() {
    super.initDescriptors();
    addGeneralDescriptors();
  }

  private void addGeneralDescriptors() {
    myDescriptors.add(createDescriptor(HighlightDisplayKey.DEPRECATED_SYMBOL, null, "Local_DeprecatedSymbol.html"));
    myDescriptors.add(createDescriptor(HighlightDisplayKey.UNUSED_IMPORT, null, "Local_UnusedImport.html"));
    myDescriptors.add(createDescriptor(HighlightDisplayKey.UNUSED_SYMBOL, null, "Local_UnusedSymbol.html"));
    myDescriptors.add(createDescriptor(HighlightDisplayKey.UNUSED_THROWS_DECL, null, "Local_UnusedThrowsDeclaration.html"));
    myDescriptors.add(createDescriptor(HighlightDisplayKey.SILLY_ASSIGNMENT, null, "Local_SillyAssignment.html"));
    myDescriptors.add(createDescriptor(HighlightDisplayKey.ACCESS_STATIC_VIA_INSTANCE, null, "Local_StaticViaInstance.html"));
    myDescriptors.add(createDescriptor(HighlightDisplayKey.WRONG_PACKAGE_STATEMENT, null, "Local_WrongPackage.html"));

    myDescriptors.add(createDescriptor(HighlightDisplayKey.ILLEGAL_DEPENDENCY, createDependencyConigurationPanel(), "Local_IllegalDependencies.html"));
    myDescriptors.add(createDescriptor(HighlightDisplayKey.JAVADOC_ERROR, null, "Local_JavaDoc.html"));

    FieldPanel myAdditionalTagsField;
    myAdditionalTagsField = new FieldPanel("Additional JavaDoc Tags", "Edit Additional JavaDoc Tags", null, null);
    myAdditionalTagsField.setPreferredSize(new Dimension(150, myAdditionalTagsField.getPreferredSize().height));
    myAdditionalTagsField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        if (mySelectedProfile != null) {
          final Document document = e.getDocument();
          try {
            final String text = document.getText(0, document.getLength());
            mySelectedProfile.setAdditionalJavadocTags(text.trim());
          }
          catch (BadLocationException e1) {
            LOG.error(e1);
          }
        }
      }
    });
    if (mySelectedProfile != null) {
      myAdditionalTagsField.setText(mySelectedProfile.getAdditionalJavadocTags());
    }
    myDescriptors.add(createDescriptor(HighlightDisplayKey.UNKNOWN_JAVADOC_TAG, myAdditionalTagsField, "Local_UnknownJavaDocTags.html"));

    myDescriptors.add(createDescriptor(HighlightDisplayKey.EJB_ERROR, null, "Local_EJBErrors.html"));
    myDescriptors.add(createDescriptor(HighlightDisplayKey.EJB_WARNING, null, "Local_EJBWarnings.html"));
    myDescriptors.add(createDescriptor(HighlightDisplayKey.UNCHECKED_WARNING, null, "Local_UncheckedWarning.html"));
  }

  private Descriptor createDescriptor(HighlightDisplayKey key, JComponent optionsPanel, String decriptionFileName) {
    return new Descriptor(HighlightDisplayKey.getDisplayNameByKey(key), key, optionsPanel, decriptionFileName, mySelectedProfile.getErrorLevel(key),
                          mySelectedProfile.isToolEnabled(key));
  }


  public static JPanel createDependencyConigurationPanel() {
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

  private JPanel getAutoreparsePanel() {
    return myPanel;
  }

  public void reset() {
    super.reset();
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    myAutoreparseDelayField.setText(Integer.toString(settings.AUTOREPARSE_DELAY));

    myCbShowImportPopup.setSelected(settings.isImportHintEnabled());

    myMarkMinHeight.setText(""+settings.getErrorStripeMarkMinHeight());
  }

  public void apply() throws ConfigurationException {
    super.apply();
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    settings.setInspectionProfile((InspectionProfileImpl)mySelectedProfile.getParentProfile());

    settings.AUTOREPARSE_DELAY = getAutoReparseDelay();
    settings.setImportHintEnabled(myCbShowImportPopup.isSelected());
    settings.setErrorStripeMarkMinHeight(getErrorStripeMarkMinHeight());

    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (int i = 0; i < projects.length; i++) {
      DaemonCodeAnalyzer.getInstance(projects[i]).settingsChanged();
    }

  }

  private int getErrorStripeMarkMinHeight() {
    return parseInteger(myMarkMinHeight);
  }

  public boolean isModified() {
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    boolean isModified = settings.AUTOREPARSE_DELAY != getAutoReparseDelay();
    isModified |= myCbShowImportPopup.isSelected() != settings.isImportHintEnabled();
    isModified |= getErrorStripeMarkMinHeight() != settings.getErrorStripeMarkMinHeight();
    if (isModified) return true;
    return super.isModified();
  }


  private int getAutoReparseDelay() {
    return parseInteger(myAutoreparseDelayField);
  }

  private static int parseInteger(final JTextField textField) {
    try {
      int delay = Integer.parseInt(textField.getText());
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

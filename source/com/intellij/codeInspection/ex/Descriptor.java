package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.packageDependencies.ui.DependencyConfigurable;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;
import org.jdom.Element;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;

/**
 * User: anna
 * Date: Dec 8, 2004
 */
public class Descriptor {
  private static Map<HighlightDisplayKey, String> ourHighlightDisplayKeyToDescriptionsMap = new HashMap<HighlightDisplayKey, String>();
  static {
    ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.DEPRECATED_SYMBOL, "Local_DeprecatedSymbol.html");
    ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.UNUSED_IMPORT, "Local_UnusedImport.html");
    ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.UNUSED_SYMBOL,  "Local_UnusedSymbol.html");
    ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.UNUSED_THROWS_DECL, "Local_UnusedThrowsDeclaration.html");
    ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.SILLY_ASSIGNMENT, "Local_SillyAssignment.html");
    ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.ACCESS_STATIC_VIA_INSTANCE, "Local_StaticViaInstance.html");
    ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.WRONG_PACKAGE_STATEMENT, "Local_WrongPackage.html");
    ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.ILLEGAL_DEPENDENCY, "Local_IllegalDependencies.html");
    ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.JAVADOC_ERROR, "Local_JavaDoc.html");
    ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.UNKNOWN_JAVADOC_TAG, "Local_UnknownJavaDocTags.html");
    ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.EJB_ERROR,  "Local_EJBErrors.html");
    ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.EJB_WARNING, "Local_EJBWarnings.html");
    ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.UNCHECKED_WARNING, "Local_UncheckedWarning.html");
  }

  private String myText;
  private String myGroup;
  private String myDescriptorFileName;
  private HighlightDisplayKey myKey;
  private JComponent myAdditionalConfigPanel;
  private Element myConfig;
  private InspectionToolsPanel.LevelChooser myChooser;
  private InspectionTool myTool;
  private HighlightDisplayLevel myLevel;
  private boolean myEnabled = false;
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.Descriptor");


  public Descriptor(HighlightDisplayKey key,
                    InspectionProfile.ModifiableModel inspectionProfile) {
    myText = HighlightDisplayKey.getDisplayNameByKey(key);
    myGroup = "General";
    myKey = key;
    myConfig = null;
    myEnabled = inspectionProfile.isToolEnabled(key);
    myChooser = new InspectionToolsPanel.LevelChooser();
    final HighlightDisplayLevel errorLevel = inspectionProfile.getErrorLevel(key);
    myChooser.setLevel(errorLevel);
    myLevel = errorLevel;
  }

  public Descriptor(InspectionTool tool, InspectionProfile.ModifiableModel inspectionProfile) {
    Element config = new Element("options");
    try {
      tool.writeExternal(config);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    myConfig = config;
    myText = tool.getDisplayName();
    myGroup = tool.getGroupDisplayName() != null && tool.getGroupDisplayName().length() == 0 ? "General" : tool.getGroupDisplayName();
    myDescriptorFileName = tool.getDescriptionFileName();
    myKey = HighlightDisplayKey.find(tool.getShortName());
    if (myKey == null) {
      myKey = HighlightDisplayKey.register(tool.getShortName());
    }
    myAdditionalConfigPanel = tool.createOptionsPanel();
    myChooser = new InspectionToolsPanel.LevelChooser();
    HighlightDisplayLevel level = inspectionProfile.getErrorLevel(myKey);
    myChooser.setLevel(level);
    myLevel = level;
    myEnabled = inspectionProfile.isToolEnabled(myKey);
    myTool = tool;
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  public void setEnabled(final boolean enabled) {
    myEnabled = enabled;
  }

  public String getText() {
    return myText;
  }

  public HighlightDisplayKey getKey() {
    return myKey;
  }

  public HighlightDisplayLevel getLevel() {
    return myLevel;
  }

  public JComponent getAdditionalConfigPanel(InspectionProfile.ModifiableModel inspectionProfile) {
    if (myKey.equals(HighlightDisplayKey.UNKNOWN_JAVADOC_TAG)){
      myAdditionalConfigPanel = createAdditionalJavadocTagsPanel(inspectionProfile);
    } else if (myAdditionalConfigPanel == null){
      if (myKey.equals(HighlightDisplayKey.ILLEGAL_DEPENDENCY) ){
        myAdditionalConfigPanel = createDependencyConigurationPanel();
      }
    }
    return myAdditionalConfigPanel;
  }

  public InspectionToolsPanel.LevelChooser getChooser() {
    return myChooser;
  }

  public void setChooserLevel(HighlightDisplayLevel level) {
    getChooser().setLevel(level);
  }

  public Element getConfig() {
    return myConfig;
  }

  public InspectionTool getTool() {
    return myTool;
  }

  public String getDescriptorFileName() {
    if (myDescriptorFileName == null){
      return ourHighlightDisplayKeyToDescriptionsMap.get(myKey);
    }
    return myDescriptorFileName;
  }

  public String getGroup() {
    return myGroup;
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

  public static FieldPanel createAdditionalJavadocTagsPanel(final InspectionProfile.ModifiableModel inspectionProfile){
    FieldPanel additionalTagsPanel = new FieldPanel("Additional JavaDoc Tags", "Edit Additional JavaDoc Tags", null, null);
    additionalTagsPanel.setPreferredSize(new Dimension(150, additionalTagsPanel.getPreferredSize().height));
    additionalTagsPanel.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        if (inspectionProfile != null) {
          final Document document = e.getDocument();
          try {
            final String text = document.getText(0, document.getLength());
            inspectionProfile.setAdditionalJavadocTags(text.trim());
          }
          catch (BadLocationException e1) {
            LOG.error(e1);
          }
        }
      }
    });
    if (inspectionProfile != null) {
      additionalTagsPanel.setText(inspectionProfile.getAdditionalJavadocTags());
    }
    return additionalTagsPanel;
  }
}

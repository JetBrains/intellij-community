package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionsBundle;
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
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
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
  @NonNls private static Map<HighlightDisplayKey, String> ourHighlightDisplayKeyToDescriptionsMap = new HashMap<HighlightDisplayKey, String>();
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
    ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.CUSTOM_HTML_TAG, "Local_CustomHtmlTags.html");
    ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.CUSTOM_HTML_ATTRIBUTE, "Local_CustomHtmlAttributes.html");
    ourHighlightDisplayKeyToDescriptionsMap.put(HighlightDisplayKey.REQUIRED_HTML_ATTRIBUTE, "Local_NotRequiredHtmlAttributes.html");
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
    myGroup = InspectionsBundle.message("inspection.general.tools.group.name");
    myKey = key;
    myConfig = null;
    myEnabled = inspectionProfile.isToolEnabled(key);
    myLevel = inspectionProfile.getErrorLevel(key);
  }

  public Descriptor(InspectionTool tool, InspectionProfile.ModifiableModel inspectionProfile) {
    @NonNls Element config = new Element("options");
    try {
      tool.writeExternal(config);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    myConfig = config;
    myText = tool.getDisplayName();
    myGroup = tool.getGroupDisplayName() != null && tool.getGroupDisplayName().length() == 0 ? InspectionsBundle.message("inspection.general.tools.group.name") : tool.getGroupDisplayName();
    myDescriptorFileName = tool.getDescriptionFileName();
    myKey = HighlightDisplayKey.find(tool.getShortName());
    if (myKey == null) {
      if (tool instanceof LocalInspectionToolWrapper) {
        myKey = HighlightDisplayKey.register(tool.getShortName(), tool.getDisplayName(), ((LocalInspectionToolWrapper)tool).getTool().getID());
      } else {
        myKey = HighlightDisplayKey.register(tool.getShortName());
      }
    }
    myLevel = inspectionProfile.getErrorLevel(myKey);
    myEnabled = inspectionProfile.isToolEnabled(myKey);
    myTool = tool;
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof Descriptor)) return false;
    final Descriptor descriptor = (Descriptor)obj;
    return myKey.equals(descriptor.getKey()) &&
           myLevel.equals(descriptor.getLevel()) &&
           myEnabled == descriptor.isEnabled();
  }

  public int hashCode() {
    return myKey.hashCode() + 29 * myLevel.hashCode();
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
    if (myAdditionalConfigPanel == null && myTool != null){
      myAdditionalConfigPanel = myTool.createOptionsPanel();
      return myAdditionalConfigPanel;
    }

    if (myKey.equals(HighlightDisplayKey.UNKNOWN_JAVADOC_TAG)){
      myAdditionalConfigPanel = createAdditionalJavadocTagsPanel(inspectionProfile);
    } else if (myKey.equals(HighlightDisplayKey.CUSTOM_HTML_TAG)){
      myAdditionalConfigPanel = createAdditionalHtmlTagsPanel(inspectionProfile);
    } else if (myKey.equals(HighlightDisplayKey.CUSTOM_HTML_ATTRIBUTE)){
      myAdditionalConfigPanel = createAdditionalHtmlAttributesPanel(inspectionProfile);
    } else if (myKey.equals(HighlightDisplayKey.REQUIRED_HTML_ATTRIBUTE)){
      myAdditionalConfigPanel = createAdditionalNotRequiredHtmlAttributesPanel(inspectionProfile);
    } else if (myKey.equals(HighlightDisplayKey.UNUSED_SYMBOL)){
      myAdditionalConfigPanel = createUnusedSymbolSettingsPanel(inspectionProfile);
    } else if (myAdditionalConfigPanel == null){
      if (myKey.equals(HighlightDisplayKey.ILLEGAL_DEPENDENCY) ){
        myAdditionalConfigPanel = createDependencyConigurationPanel();
      }
    }
    return myAdditionalConfigPanel;
  }

  public void resetConfigPanel(){
    myAdditionalConfigPanel = null;
  }

  public InspectionToolsPanel.LevelChooser getChooser() {
    if (myChooser == null){
      myChooser = new InspectionToolsPanel.LevelChooser();
      myChooser.setLevel(myLevel);
    }
    return myChooser;
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
    final JButton editDependencies = new JButton(InspectionsBundle.message("inspection.dependency.configure.button.text"));
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
    FieldPanel additionalTagsPanel = new FieldPanel(InspectionsBundle.message("inspection.javadoc.label.text"), InspectionsBundle.message("inspection.javadoc.dialog.title"), null, null);
    additionalTagsPanel.setPreferredSize(new Dimension(150, additionalTagsPanel.getPreferredSize().height));
    additionalTagsPanel.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        if (inspectionProfile != null) {
          final Document document = e.getDocument();
          try {
            final String text = document.getText(0, document.getLength());
            if (text != null) {
              inspectionProfile.setAdditionalJavadocTags(text.trim());
            }
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

  public static FieldPanel createAdditionalHtmlTagsPanel(final InspectionProfile.ModifiableModel inspectionProfile){
    FieldPanel additionalTagsPanel = new FieldPanel(InspectionsBundle.message("inspection.javadoc.html.label.text"), InspectionsBundle.message("inspection.javadoc.html.dialog.title"), null, null);
    additionalTagsPanel.setPreferredSize(new Dimension(150, additionalTagsPanel.getPreferredSize().height));
    additionalTagsPanel.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        if (inspectionProfile != null) {
          final Document document = e.getDocument();
          try {
            final String text = document.getText(0, document.getLength());
            if (text != null) {
              inspectionProfile.setAdditionalHtmlTags(text.trim());
            }
          }
          catch (BadLocationException e1) {
            LOG.error(e1);
          }
        }
      }
    });
    if (inspectionProfile != null) {
      additionalTagsPanel.setText(inspectionProfile.getAdditionalHtmlTags());
    }
    return additionalTagsPanel;
  }
  
  public static FieldPanel createAdditionalHtmlAttributesPanel(final InspectionProfile.ModifiableModel inspectionProfile){
    FieldPanel additionalAttributesPanel = new FieldPanel(InspectionsBundle.message("inspection.javadoc.html.attributes.label.text"), InspectionsBundle.message("inspection.javadoc.html.attributes.dialog.title"), null, null);
    
    additionalAttributesPanel.setPreferredSize(new Dimension(150, additionalAttributesPanel.getPreferredSize().height));
    additionalAttributesPanel.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        if (inspectionProfile != null) {
          final Document document = e.getDocument();
          try {
            final String text = document.getText(0, document.getLength());
            if (text != null) {
              inspectionProfile.setAdditionalHtmlAttributes(text.trim());
            }
          }
          catch (BadLocationException e1) {
            LOG.error(e1);
          }
        }
      }
    });
    if (inspectionProfile != null) {
      additionalAttributesPanel.setText(inspectionProfile.getAdditionalHtmlAttributes());
    }
    return additionalAttributesPanel;
  }
  
  public static FieldPanel createAdditionalNotRequiredHtmlAttributesPanel(final InspectionProfile.ModifiableModel inspectionProfile){
    FieldPanel additionalAttributesPanel = new FieldPanel(InspectionsBundle.message("inspection.javadoc.html.not.required.label.text"), InspectionsBundle.message("inspection.javadoc.html.not.required.dialog.title"), null, null);
    
    additionalAttributesPanel.setPreferredSize(new Dimension(150, additionalAttributesPanel.getPreferredSize().height));
    additionalAttributesPanel.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        if (inspectionProfile != null) {
          final Document document = e.getDocument();
          try {
            final String text = document.getText(0, document.getLength());
            if (text != null) {
              inspectionProfile.setAdditionalNotRequiredHtmlAttributes(text.trim());
            }
          }
          catch (BadLocationException e1) {
            LOG.error(e1);
          }
        }
      }
    });
    if (inspectionProfile != null) {
      additionalAttributesPanel.setText(inspectionProfile.getAdditionalNotRequiredHtmlAttributes());
    }
    return additionalAttributesPanel;
  }
  
  public static JPanel createUnusedSymbolSettingsPanel(final InspectionProfile.ModifiableModel inspectionProfile){
    JPanel panel = new JPanel(new GridLayout(5, 1, 2, 2));
    final JCheckBox local = new JCheckBox(InspectionsBundle.message("inspection.unused.symbol.option"));
    final JCheckBox field = new JCheckBox(InspectionsBundle.message("inspection.unused.symbol.option1"));
    final JCheckBox method = new JCheckBox(InspectionsBundle.message("inspection.unused.symbol.option2"));
    final JCheckBox classes = new JCheckBox(InspectionsBundle.message("inspection.unused.symbol.option3"));
    final JCheckBox parameters = new JCheckBox(InspectionsBundle.message("inspection.unused.symbol.option4"));
    ChangeListener listener = new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        InspectionProfile.UnusedSymbolSettings settings = new InspectionProfile.UnusedSymbolSettings();
        settings.LOCAL_VARIABLE = local.isSelected();
        settings.CLASS = classes.isSelected();
        settings.FIELD = field.isSelected();
        settings.PARAMETER = parameters.isSelected();
        settings.METHOD = method.isSelected();
        inspectionProfile.setUnusedSymbolSettings(settings);
      }
    };
    local.addChangeListener(listener);
    field.addChangeListener(listener);
    method.addChangeListener(listener);
    classes.addChangeListener(listener);
    parameters.addChangeListener(listener);
    panel.add(local);
    panel.add(field);
    panel.add(method);
    panel.add(classes);
    panel.add(parameters);
    if (inspectionProfile != null){
      final InspectionProfile.UnusedSymbolSettings unusedSymbolSettings = inspectionProfile.getUnusedSymbolSettings();
      local.setSelected(unusedSymbolSettings.LOCAL_VARIABLE);
      field.setSelected(unusedSymbolSettings.FIELD);
      method.setSelected(unusedSymbolSettings.METHOD);
      classes.setSelected(unusedSymbolSettings.CLASS);
      parameters.setSelected(unusedSymbolSettings.PARAMETER);
    }
    JPanel doNotExpand = new JPanel(new BorderLayout());
    doNotExpand.add(panel, BorderLayout.NORTH);
    return doNotExpand;
  }
}

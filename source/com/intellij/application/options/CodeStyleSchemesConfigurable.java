package com.intellij.application.options;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemeImpl;
import com.intellij.util.Alarm;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

public class CodeStyleSchemesConfigurable implements Configurable, ApplicationComponent {
  private static final String WAIT_CARD = "CodeStyleSchemesConfigurable.$$$.Wait.placeholder.$$$";
  private JPanel myPanel;
  private JComboBox myCombo;
  private JButton mySaveAsButton;
  private JButton myDeleteButton;
  private SettingsStack mySettingsStack;

  private static class SettingsStack extends JPanel {
    private CardLayout myLayout;
    private Map<String, CodeStyleSettingsPanel> mySchemes = new HashMap<String, CodeStyleSettingsPanel>();
    private Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
    private String mySelectedScheme = null;

    public SettingsStack(CodeStyleSchemes codeStyleSchemes, EditorSettingsExternalizable editorSettingsExternalizable) {
      myLayout = new CardLayout();
      setLayout(myLayout);

      addWaitCard();

      CodeStyleSettings codeStyleSettings = codeStyleSchemes.getCurrentScheme().getCodeStyleSettings();
      EditorSettingsExternalizable editorSettings = editorSettingsExternalizable;
      editorSettings.setBlockIndent(codeStyleSettings.getIndentSize(null));
    }

    private void addWaitCard() {
      JPanel waitPanel = new JPanel(new BorderLayout());
      JLabel label = new JLabel("Loading page. Please wait.");
      label.setHorizontalAlignment(SwingConstants.CENTER);
      waitPanel.add(label, BorderLayout.CENTER);
      label.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      waitPanel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      add(WAIT_CARD, waitPanel);
      mySelectedScheme = null;
    }

    public void addNotify() {
      super.addNotify();
      myLayout.show(this, WAIT_CARD);
    }

    public void addScheme(CodeStyleScheme scheme) {
      CodeStyleSettings settings = scheme.getCodeStyleSettings();
      if (scheme.isDefault()) {
        settings = (CodeStyleSettings)settings.clone();
      }
      final CodeStyleSettingsPanel settingsPanel = new CodeStyleSettingsPanel(settings);
      add(scheme.getName(), settingsPanel);
      mySchemes.put(scheme.getName(), settingsPanel);
    }

    public void removeScheme(CodeStyleScheme scheme) {
      final CodeStyleSettingsPanel panel = mySchemes.remove(scheme.getName());
      if (panel != null) {
        remove(panel);
      }
    }

    public void selectScheme(final CodeStyleScheme scheme) {
      myLayout.show(this, WAIT_CARD);

      myAlarm.cancelAllRequests();
      final Runnable request = new Runnable() {
        public void run() {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              String name = scheme.getName();
              mySchemes.get(name).init();
              myLayout.show(SettingsStack.this, name);
              mySelectedScheme = name;
            }
          });
        }
      };
      myAlarm.addRequest(request, 200);
    }

    public CodeStyleSettingsPanel[] getPanels() {
      final Collection<CodeStyleSettingsPanel> panels = mySchemes.values();
      return panels.toArray(new CodeStyleSettingsPanel[panels.size()]);
    }

    public boolean isModified() {
      final CodeStyleSettingsPanel[] panels = getPanels();
      for (int i = 0; i < panels.length; i++) {
        CodeStyleSettingsPanel panel = panels[i];
        if (panel.isModified()) return true;
      }
      return false;
    }

    public void reset() {
      final CodeStyleSettingsPanel[] panels = getPanels();
      for (int i = 0; i < panels.length; i++) {
        CodeStyleSettingsPanel panel = panels[i];
        if (panel.isModified()) panel.reset();
      }
    }

    public void apply() {
      final CodeStyleSettingsPanel[] panels = getPanels();
      for (int i = 0; i < panels.length; i++) {
        CodeStyleSettingsPanel panel = panels[i];
        if (panel.isModified()) panel.apply();
      }
    }

    public void dispose() {
      myAlarm.cancelAllRequests();
      final CodeStyleSettingsPanel[] panels = getPanels();
      for (int i = 0; i < panels.length; i++) {
        CodeStyleSettingsPanel panel = panels[i];
        panel.dispose();
      }
    }

    public String getHelpTopic() {
      CodeStyleSettingsPanel selectedPanel = mySchemes.get(mySelectedScheme);
      if (selectedPanel == null) {
        return "preferences.sourceCode";
      }
      return selectedPanel.getHelpTopic();
    }

    public void removeAllSchemes() {
      dispose();
      removeAll();
      mySchemes = new com.intellij.util.containers.HashMap<String, CodeStyleSettingsPanel>();
      addWaitCard();
    }

    public boolean isSchemeModified(CodeStyleScheme scheme) {
      return mySchemes.get(scheme.getName()).isModified();
    }

    public CodeStyleSettings getSettings(CodeStyleScheme defaultScheme) {
      return mySchemes.get(defaultScheme.getName()).getSettings();
    }
    public CodeStyleSettingsPanel getSettingsPanel(CodeStyleScheme defaultScheme) {
      return mySchemes.get(defaultScheme.getName());
    }
  }

  public void disposeComponent() {
  }

  public void initComponent() {
  }

  public boolean isModified() {
    if (myPanel == null) return false; // Disposed
    if (getSelectedScheme() != CodeStyleSchemes.getInstance().getCurrentScheme()) return true;
    Set configuredSchemesSet = new HashSet(getCurrentSchemes());
    Set savedSchemesSet = new HashSet(Arrays.asList(CodeStyleSchemes.getInstance().getSchemes()));
    if (!configuredSchemesSet.equals(savedSchemesSet)) return true;
    return mySettingsStack.isModified();
  }

  public JComponent createComponent() {
    mySettingsStack = new SettingsStack(CodeStyleSchemes.getInstance(), EditorSettingsExternalizable.getInstance());

    myPanel = new JPanel(new GridBagLayout());
    Insets stdInsets = new Insets(2, 2, 2, 2);

    myCombo = new JComboBox();
    mySaveAsButton = new JButton("Save As...");
    myDeleteButton = new JButton("Delete");

    int row = 0;
    // 1st row
    myPanel.add(new JLabel("Scheme name:"),
                new GridBagConstraints(0, row, 1, 1, 0, 0, GridBagConstraints.EAST, GridBagConstraints.NONE, stdInsets,
                                       0, 0));
    myPanel.add(myCombo,
                new GridBagConstraints(1, row, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                       stdInsets, 70, 0));
    myPanel.add(mySaveAsButton,
                new GridBagConstraints(2, row, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                       stdInsets, 0, 0));
    myPanel.add(myDeleteButton,
                new GridBagConstraints(3, row, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                       stdInsets, 0, 0));

    myPanel.add(new JPanel(),
                new GridBagConstraints(4, row, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                       stdInsets, 0, 0));
    // next row
    row++;
    myPanel.add(mySettingsStack,
                new GridBagConstraints(0, row, 5, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                       stdInsets, 0, 0));

    myCombo.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                onCombo();
              }
            });
      }
    });
    mySaveAsButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        onSaveAs();
      }
    });
    myDeleteButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        onDelete();
      }
    });

    reset();

    myPanel.setPreferredSize(new Dimension(800, 650));

    final Project project = (Project)DataManager.getInstance().getDataContext().getData(DataConstants.PROJECT);
    if (project == null || !CodeStyleSettingsManager.getInstance(project).USE_PER_PROJECT_SETTINGS) return myPanel;

    final CardLayout cards = new CardLayout();
    final JPanel rootPanel = new JPanel(cards);

    rootPanel.add("Settings", myPanel);

    final JPanel warningPanel =  new JPanel(new VerticalFlowLayout(VerticalFlowLayout.CENTER));
    final JLabel label = new JLabel("<html><body>The current project is configured to use its own code style.<br>" +
                                    "Changes made to global code style settings will not affect formatting in the current project.<br>" +
                                    "See Project Settings | Code Style.<br>" +
                                    "Press &quot;Edit Global Settings&quot; button below if you still want to edit global settings.</body></html>");
    label.setIcon(IconLoader.getIcon("/general/tip.png"));
    label.setHorizontalAlignment(SwingConstants.CENTER);
    warningPanel.add(label);

    JButton editGlobal = new JButton("Edit Global Settings");
    editGlobal.setMnemonic('G');

    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
    buttonPanel.add(editGlobal);
    warningPanel.add(buttonPanel);

    editGlobal.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        cards.show(rootPanel, "Settings");
      }
    });

    rootPanel.add("Warning", warningPanel);
    cards.show(rootPanel, "Warning");

    return rootPanel;
  }

  public String getDisplayName() {
    return "Global\nCode Style";
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableCodeStyle.png");
  }

  public CodeStyleSettingsPanel getActivePanel() {
    CodeStyleScheme currentScheme = getSelectedScheme();
    return mySettingsStack.getSettingsPanel(currentScheme);
  }

  public void reset() {
    mySettingsStack.removeAllSchemes();
    initCombobox();
    onCombo();
  }

  public void apply() throws ConfigurationException {
    final CodeStyleScheme[] savedSchemes = CodeStyleSchemes.getInstance().getSchemes();
    final Set savedSchemesSet = new HashSet(Arrays.asList(savedSchemes));
    List<CodeStyleScheme> configuredSchemes = getCurrentSchemes();

    for (int i = 0; i < savedSchemes.length; i++) {
      CodeStyleScheme savedScheme = savedSchemes[i];
      if (!configuredSchemes.contains(savedScheme)) {
        CodeStyleSchemes.getInstance().deleteScheme(savedScheme);
      }
    }

    for (int i = 0; i < configuredSchemes.size(); i++) {
      CodeStyleScheme scheme = configuredSchemes.get(i);
      if (!savedSchemesSet.contains(scheme)) {
        CodeStyleSchemes.getInstance().addScheme(scheme);
      }
    }

    CodeStyleScheme currentScheme = getSelectedScheme();
    final boolean isDefaultModified = currentScheme.isDefault() && mySettingsStack.isSchemeModified(currentScheme);
    mySettingsStack.apply();
    if (isDefaultModified) {
      final CodeStyleScheme defaultScheme = currentScheme;
      currentScheme = CodeStyleSchemes.getInstance().createNewScheme(null, defaultScheme);
      ((CodeStyleSchemeImpl)currentScheme).setCodeStyleSettings(mySettingsStack.getSettings(defaultScheme));
      CodeStyleSchemes.getInstance().addScheme(currentScheme);
    }
    CodeStyleSchemes.getInstance().setCurrentScheme(currentScheme);
    if (isDefaultModified) {
      initCombobox();
    }
    EditorFactory.getInstance().refreshAllEditors();
  }

  private List<CodeStyleScheme> getCurrentSchemes() {
    List<CodeStyleScheme> configuredSchemes = new ArrayList<CodeStyleScheme>();
    final DefaultComboBoxModel model = (DefaultComboBoxModel)myCombo.getModel();
    for (int i = 0; i < model.getSize(); i++) {
      configuredSchemes.add((CodeStyleScheme)model.getElementAt(i));
    }
    return configuredSchemes;
  }

  public void disposeUIResources() {
    if (myPanel != null) {
      myPanel.removeAll();
      myPanel = null;
    }

    if (mySettingsStack != null) {
      mySettingsStack.dispose();
      mySettingsStack = null;
    }
  }

  public String getHelpTopic() {
    return mySettingsStack.getHelpTopic();
  }

  public String getComponentName() {
    return "CodeStyleOptions";
  }

  private void onDelete() {
    final CodeStyleScheme scheme = getSelectedScheme();
    mySettingsStack.removeScheme(scheme);
    ((DefaultComboBoxModel)myCombo.getModel()).removeElement(scheme);
  }

  private void onSaveAs() {
    CodeStyleScheme[] schemes = CodeStyleSchemes.getInstance().getSchemes();
    ArrayList names = new ArrayList();
    for (int i = 0; i < schemes.length; i++) {
      CodeStyleScheme scheme = schemes[i];
      names.add(scheme.getName());
    }
    SaveSchemeDialog saveDialog = new SaveSchemeDialog(myPanel, "Save Code Style Scheme As", names);
    saveDialog.show();
    if (saveDialog.isOK()) {
      CodeStyleScheme selectedScheme = getSelectedScheme();
      CodeStyleScheme newScheme = CodeStyleSchemes.getInstance().createNewScheme(saveDialog.getSchemeName(),
                                                                                 selectedScheme);
      mySettingsStack.addScheme(newScheme);
      ((DefaultComboBoxModel)myCombo.getModel()).addElement(newScheme);
      myCombo.setSelectedItem(newScheme);
    }
  }

  private void onCombo() {
    if (myPanel == null) return; // disposed.

    CodeStyleScheme selected = getSelectedScheme();
    if (selected != null) {
      mySettingsStack.selectScheme(selected);
      updateButtons();
    }
  }

  private void updateButtons() {
    boolean deleteEnabled = false;
    boolean saveAsEnabled = true;
    CodeStyleScheme selected = getSelectedScheme();
    if (selected != null) {
      deleteEnabled = !selected.isDefault();
    }
    myDeleteButton.setEnabled(deleteEnabled);
    mySaveAsButton.setEnabled(saveAsEnabled);
  }

  private CodeStyleScheme getSelectedScheme() {
    Object selected = myCombo.getSelectedItem();
    if (selected instanceof CodeStyleScheme) {
      return (CodeStyleScheme)selected;
    }
    return null;
  }

  private void initCombobox() {
    CodeStyleScheme[] schemes = CodeStyleSchemes.getInstance().getSchemes();
    Vector schemesVector = new Vector();
    for (int i = 0; i < schemes.length; i++) {
      schemesVector.addElement(schemes[i]);
      mySettingsStack.addScheme(schemes[i]);
    }
    DefaultComboBoxModel model = new DefaultComboBoxModel(schemesVector);
    myCombo.setModel(model);
    myCombo.setSelectedItem(CodeStyleSchemes.getInstance().getCurrentScheme());
    if (myCombo.getSelectedItem() == null) {
      if (myCombo.getItemCount() > 0) {
        myCombo.setSelectedIndex(0);
      }
    }
    updateButtons();
  }

  public static CodeStyleSchemesConfigurable getInstance() {
    return ApplicationManager.getApplication().getComponent(CodeStyleSchemesConfigurable.class);
  }

  public void selectPage(Class pageToSelect) {
    getActivePanel().selectTab(pageToSelect);
  }
}
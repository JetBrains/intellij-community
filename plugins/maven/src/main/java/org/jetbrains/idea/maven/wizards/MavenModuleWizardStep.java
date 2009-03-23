package org.jetbrains.idea.maven.wizards;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.SpeedSearchBase;
import com.intellij.util.Function;
import com.intellij.util.ui.UIUtil;
import org.apache.maven.archetype.catalog.Archetype;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.indices.MavenIndicesManager;
import org.jetbrains.idea.maven.navigator.SelectMavenProjectDialog;
import org.jetbrains.idea.maven.project.MavenProjectModel;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenId;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MavenModuleWizardStep extends ModuleWizardStep {
  private static final Icon WIZARD_ICON = IconLoader.getIcon("/addmodulewizard.png");

  private static final String INHERIT_GROUP_ID_KEY = "MavenModuleWizard.inheritGroupId";
  private static final String INHERIT_VERSION_KEY = "MavenModuleWizard.inheritVersion";
  private static final String ARCHETYPE_ARTIFACT_ID_KEY = "MavenModuleWizard.archetypeArtifactIdKey";
  private static final String ARCHETYPE_GROUP_ID_KEY = "MavenModuleWizard.archetypeGroupIdKey";
  private static final String ARCHETYPE_VERSION_KEY = "MavenModuleWizard.archetypeVersionKey";

  private final Project myProjectOrNull;
  private final MavenModuleBuilder myBuilder;
  private MavenProjectModel myAggregator;
  private MavenProjectModel myParent;

  private String myInheritedGroupId;
  private String myInheritedVersion;

  private JPanel myMainPanel;

  private JLabel myAggregatorLabel;
  private JLabel myAggregatorNameLabel;
  private JButton mySelectAggregator;

  private JLabel myParentLabel;
  private JLabel myParentNameLabel;
  private JButton mySelectParent;

  private JTextField myGroupIdField;
  private JCheckBox myInheritGroupIdCheckBox;
  private JTextField myArtifactIdField;
  private JTextField myVersionField;
  private JCheckBox myInheritVersionCheckBox;

  private JCheckBox myUseArchetypeCheckBox;
  private JList myArchetypesList;

  public MavenModuleWizardStep(@Nullable Project project, MavenModuleBuilder builder) {
    myProjectOrNull = project;
    myBuilder = builder;

    initComponents();
    loadSettings();
  }

  private void initComponents() {
    mySelectAggregator.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myAggregator = selectProject(myAggregator);
        updateComponents();
      }
    });

    mySelectParent.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myParent = selectProject(myParent);
        updateComponents();
      }
    });

    ActionListener updatingListener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateComponents();
      }
    };
    myInheritGroupIdCheckBox.addActionListener(updatingListener);
    myInheritVersionCheckBox.addActionListener(updatingListener);

    myUseArchetypeCheckBox.addActionListener(updatingListener);
    myArchetypesList.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myArchetypesList.setCellRenderer(new MyCellRenderer());

    new ListSpeedSearch(myArchetypesList, new Function<Object, String>() {
      public String fun(Object o) {
        Archetype a = (Archetype)o;
        return a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion();
      }
    }).setComparator(new SpeedSearchBase.SpeedSearchComparator(false) {
      @Override
      public void translateCharacter(StringBuilder buf, char ch) {
        if (ch == '*') {
          buf.append("(.)*");
        }
        else {
          super.translateCharacter(buf, ch);
        }
      }
    });
  }

  private MavenProjectModel selectProject(MavenProjectModel current) {
    assert myProjectOrNull != null : "must not be called when creating a new project";

    SelectMavenProjectDialog d = new SelectMavenProjectDialog(myProjectOrNull, current);
    d.show();
    if (!d.isOK()) return current;
    return d.getResult();
  }

  @Override
  public void onStepLeaving() {
    saveSettings();
  }

  private void loadSettings() {
    myBuilder.setInheritedOptions(getSavedValue(INHERIT_GROUP_ID_KEY, true),
                                  getSavedValue(INHERIT_VERSION_KEY, true));

    String archGroupId = getSavedValue(ARCHETYPE_GROUP_ID_KEY, null);
    String archArtifactId = getSavedValue(ARCHETYPE_ARTIFACT_ID_KEY, null);
    String archVersion = getSavedValue(ARCHETYPE_VERSION_KEY, null);
    if (archGroupId == null || archArtifactId == null || archVersion == null) {
      myBuilder.setArchetypeId(null);
    }
    else {
      myBuilder.setArchetypeId(new MavenId(archGroupId, archArtifactId, archVersion));
    }
  }

  private void saveSettings() {
    saveValue(INHERIT_GROUP_ID_KEY, myInheritGroupIdCheckBox.isSelected());
    saveValue(INHERIT_VERSION_KEY, myInheritVersionCheckBox.isSelected());

    MavenId arch = getSelectedArtifactId();
    String archGroupId = arch == null ? null : arch.groupId;
    String archArtifactId = arch == null ? null : arch.artifactId;
    String archVersion = arch == null ? null : arch.version;

    saveValue(ARCHETYPE_GROUP_ID_KEY, archGroupId);
    saveValue(ARCHETYPE_ARTIFACT_ID_KEY, archArtifactId);
    saveValue(ARCHETYPE_VERSION_KEY, archVersion);
  }

  private boolean getSavedValue(String key, boolean defaultValue) {
    return getSavedValue(key, String.valueOf(defaultValue)).equals(String.valueOf(true));
  }

  private String getSavedValue(String key, String defaultValue) {
    String value = PropertiesComponent.getInstance().getValue(key);
    return value == null ? defaultValue : value;
  }

  private void saveValue(String key, boolean value) {
    saveValue(key, String.valueOf(value));
  }

  private void saveValue(String key, String value) {
    PropertiesComponent props = PropertiesComponent.getInstance();
    props.setValue(key, value);
  }

  public JComponent getComponent() {
    return myMainPanel;
  }

  @Override
  public void updateStep() {
    if (isMavenizedProject()) {
      MavenProjectModel parent = myBuilder.findPotentialParentProject(myProjectOrNull);
      myAggregator = parent;
      myParent = parent;
    }

    myArtifactIdField.setText(myBuilder.getName());
    myGroupIdField.setText(myParent == null ? myBuilder.getName() : myParent.getMavenId().groupId);
    myVersionField.setText(myParent == null ? "1.0" : myParent.getMavenId().version);

    MavenId selectedArchId = getSelectedArtifactId();
    if (selectedArchId == null && myUseArchetypeCheckBox.isSelected()) {
      selectedArchId = myBuilder.getArchetypeId();
    }

    DefaultListModel model = new DefaultListModel();
    List<Archetype> archetypes = new ArrayList<Archetype>(MavenIndicesManager.getInstance().getArchetypes());

    Collections.sort(archetypes, new Comparator<Archetype>() {
      public int compare(Archetype o1, Archetype o2) {
        String key1 = o1.getGroupId() + ":" + o1.getArtifactId();
        String key2 = o2.getGroupId() + ":" + o2.getArtifactId();

        int result = key1.compareToIgnoreCase(key2);
        if (result != 0) return result;

        return o2.getVersion().compareToIgnoreCase(o1.getVersion());
      }
    });

    Archetype selectedArch = null;
    for (Archetype each : archetypes) {
      if (getArchetypeId(each).equals(selectedArchId)) selectedArch = each;
      model.addElement(each);
    }
    myArchetypesList.setModel(model);
    myArchetypesList.setSelectedValue(selectedArch, true);

    updateComponents();
  }

  private boolean isMavenizedProject() {
    return myProjectOrNull != null
           && MavenProjectsManager.getInstance(myProjectOrNull).isInitialized()
           && MavenProjectsManager.getInstance(myProjectOrNull).isMavenizedProject();
  }

  private void updateComponents() {
    if (!isMavenizedProject()) {
      myAggregatorLabel.setEnabled(false);
      myAggregatorNameLabel.setEnabled(false);
      mySelectAggregator.setEnabled(false);

      myParentLabel.setEnabled(false);
      myParentNameLabel.setEnabled(false);
      mySelectParent.setEnabled(false);
    }
    myAggregatorNameLabel.setText(formatProjectString(myAggregator));
    myParentNameLabel.setText(formatProjectString(myParent));

    if (myParent == null) {
      myGroupIdField.setEnabled(true);
      myVersionField.setEnabled(true);
      myInheritGroupIdCheckBox.setEnabled(false);
      myInheritVersionCheckBox.setEnabled(false);
    }
    else {
      myGroupIdField.setEnabled(!myInheritGroupIdCheckBox.isSelected());
      myVersionField.setEnabled(!myInheritVersionCheckBox.isSelected());

      if (myInheritGroupIdCheckBox.isSelected()
          || myGroupIdField.getText().equals(myInheritedGroupId)) {
        myGroupIdField.setText(myParent.getMavenId().groupId);
      }
      if (myInheritVersionCheckBox.isSelected()
          || myVersionField.getText().equals(myInheritedVersion)) {
        myVersionField.setText(myParent.getMavenId().version);
      }
      myInheritedGroupId = myGroupIdField.getText();
      myInheritedVersion = myVersionField.getText();

      myInheritGroupIdCheckBox.setEnabled(true);
      myInheritVersionCheckBox.setEnabled(true);
    }

    if (myUseArchetypeCheckBox.isSelected()) {
      myArchetypesList.setEnabled(true);
      myArchetypesList.setBackground(UIUtil.getListBackground());
    }
    else {
      myArchetypesList.setEnabled(false);
      myArchetypesList.setBackground(UIUtil.getComboBoxDisabledBackground());
    }
  }

  private String formatProjectString(MavenProjectModel project) {
    if (project == null) return "<none>";
    MavenId id = project.getMavenId();
    return id.groupId + ":" + id.artifactId + ":" + id.version;
  }

  @Override
  public void updateDataModel() {
    myBuilder.setAggregatorProject(myAggregator);
    myBuilder.setParentProject(myParent);

    myBuilder.setProjectId(new MavenId(myGroupIdField.getText(),
                                       myArtifactIdField.getText(),
                                       myVersionField.getText()));

    myBuilder.setArchetypeId(getSelectedArtifactId());
  }

  private MavenId getSelectedArtifactId() {
    if (!myUseArchetypeCheckBox.isSelected() || myArchetypesList.isSelectionEmpty()) return null;
    return getArchetypeId((Archetype)myArchetypesList.getSelectedValue());
  }

  private MavenId getArchetypeId(Archetype arch) {
    return new MavenId(arch.getGroupId(), arch.getArtifactId(), arch.getVersion());
  }

  @Override
  public Icon getIcon() {
    return WIZARD_ICON;
  }

  private class MyCellRenderer extends ColoredListCellRenderer {
    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      Archetype archetype = (Archetype)value;

      append(archetype.getGroupId() + ":", SimpleTextAttributes.GRAY_ATTRIBUTES);
      append(archetype.getArtifactId(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      append(":" + archetype.getVersion(), SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  @Override
  public String getHelpId() {
    return "reference.dialogs.new.project.fromScratch.maven";
  }
}

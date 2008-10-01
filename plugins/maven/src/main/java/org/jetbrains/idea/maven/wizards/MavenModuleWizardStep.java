package org.jetbrains.idea.maven.wizards;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.NullNode;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.ui.UIUtil;
import org.apache.maven.archetype.catalog.Archetype;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.indices.MavenIndicesManager;
import org.jetbrains.idea.maven.navigator.MavenTreeStructure;
import org.jetbrains.idea.maven.navigator.SelectFromMavenTreeDialog;
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
  private static final Icon WIZARD_ICON = IconLoader.getIcon("/add_java_modulewizard.png");

  private static final String INHERIT_GROUP_ID_KEY = "MavenModuleWizard.inheritGroupId";
  private static final String INHERIT_VERSION_KEY = "MavenModuleWizard.inheritVersion";
  private static final String USE_ARCHETYPE_KEY = "MavenModuleWizard.useArchetypeKey";

  private Project myProjectOrNull;
  private MavenModuleBuilder myBuilder;
  private MavenProjectModel myAggregator;
  private MavenProjectModel myParent;

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
  }

  private MavenProjectModel selectProject(MavenProjectModel current) {
    assert myProjectOrNull != null : "must not be called when creating a new project";

    MySelectMavenProjectDialog d = new MySelectMavenProjectDialog(current);
    d.show();
    if (!d.isOK()) return current;
    return d.getResult();
  }

  @Override
  public void onStepLeaving() {
    saveSettings();
  }

  private void loadSettings() {
    myInheritGroupIdCheckBox.setSelected(getSavedValue(INHERIT_GROUP_ID_KEY, true));
    myInheritVersionCheckBox.setSelected(getSavedValue(INHERIT_VERSION_KEY, true));
    myUseArchetypeCheckBox.setSelected(getSavedValue(USE_ARCHETYPE_KEY, false));
  }

  private void saveSettings() {
    saveValue(INHERIT_GROUP_ID_KEY, myInheritGroupIdCheckBox.isSelected());
    saveValue(INHERIT_VERSION_KEY, myInheritVersionCheckBox.isSelected());
    saveValue(USE_ARCHETYPE_KEY, myUseArchetypeCheckBox.isSelected());
  }

  private boolean getSavedValue(String key, boolean defaultValue) {
    String value = PropertiesComponent.getInstance().getValue(key);
    return value == null ? defaultValue : "true".equals(value);
  }

  private void saveValue(String key, boolean value) {
    PropertiesComponent props = PropertiesComponent.getInstance();
    props.setValue(key, value ? "true" : "false");
  }

  public JComponent getComponent() {
    return myMainPanel;
  }

  @Override
  public void updateStep() {
    if (myProjectOrNull != null) {
      VirtualFile parentPom = myBuilder.findContentEntry().getParent().findChild("pom.xml");

      MavenProjectModel parent = null;
      if (parentPom != null) {
        parent = MavenProjectsManager.getInstance(myProjectOrNull).findProject(parentPom);
      }
      myAggregator = parent;
      myParent = parent;
    }

    myArtifactIdField.setText(myBuilder.getName());
    myGroupIdField.setText(myParent == null ? myBuilder.getName() : myParent.getMavenId().groupId);
    myVersionField.setText(myParent == null ? "1.0" : myParent.getMavenId().version);

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

    for (Archetype each : archetypes) {
      model.addElement(each);
    }
    myArchetypesList.setModel(model);

    updateComponents();
  }

  private void updateComponents() {
    if (myProjectOrNull == null) {
      myAggregatorLabel.setEnabled(false);
      myAggregatorNameLabel.setEnabled(false);
      mySelectAggregator.setEnabled(false);

      myParentLabel.setEnabled(false);
      myParentNameLabel.setEnabled(false);
      mySelectParent.setEnabled(false);
    }
    myAggregatorNameLabel.setText(formatProjectString(myAggregator));
    myParentNameLabel.setText(formatProjectString(myParent));

    myInheritGroupIdCheckBox.setEnabled(myParent != null);
    myInheritVersionCheckBox.setEnabled(myParent != null);

    if (myInheritGroupIdCheckBox.isEnabled()) {
      myGroupIdField.setText(myParent.getMavenId().groupId);
    }
    myGroupIdField.setEnabled(!myInheritGroupIdCheckBox.isSelected());

    if (myInheritVersionCheckBox.isEnabled()) {
      myVersionField.setText(myParent.getMavenId().version);
    }
    myVersionField.setEnabled(!myInheritVersionCheckBox.isSelected());

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

    if (myUseArchetypeCheckBox.isSelected()) {
      Archetype arch = (Archetype)myArchetypesList.getSelectedValue();
      myBuilder.setArchetypeId(new MavenId(arch.getGroupId(),
                                           arch.getArtifactId(),
                                           arch.getVersion()));
    }
    else {
      myBuilder.setArchetypeId(null);
    }
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

  private class MySelectMavenProjectDialog extends SelectFromMavenTreeDialog {
    private MavenProjectModel myResult;

    public MySelectMavenProjectDialog(final MavenProjectModel current) {
      super(myProjectOrNull, "Select Maven Project", MavenTreeStructure.PomNode.class, new NodeSelector() {
        public SimpleNode findNode(MavenTreeStructure.PomNode pomNode) {
          return pomNode.getProjectModel() == current ? pomNode : null;
        }
      });

      init();
    }

    @Override
    protected Action[] createActions() {
      Action selectNoneAction = new AbstractAction("&None") {
        public void actionPerformed(ActionEvent e) {
          doOKAction();
          myResult = null;
        }
      };
      return new Action[]{selectNoneAction, getOKAction(), getCancelAction()};
    }

    @Override
    protected void doOKAction() {
      SimpleNode node = getSelectedNode();
      if (node instanceof NullNode) node = null;

      if (node != null) {
        if (!(node instanceof MavenTreeStructure.PomNode)) {
          ((MavenTreeStructure.CustomNode)node).getParent(MavenTreeStructure.PomNode.class);
        }
      }
      myResult = node != null ? ((MavenTreeStructure.PomNode)node).getProjectModel() : null;

      super.doOKAction();
    }

    public MavenProjectModel getResult() {
      return myResult;
    }
  }
}

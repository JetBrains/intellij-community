
package com.intellij.refactoring.migration;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.refactoring.HelpID;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.IOException;

public class MigrationDialog extends DialogWrapper{
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.migration.MigrationDialog");

  private JPanel myPanel;
  private JComboBox myMapComboBox;
  private JTextArea myDescriptionTextArea;
  private JButton myEditMapButton;
  private JButton myNewMapButton;
  private JButton myRemoveMapButton;
  private Project myProject;
  private MigrationMapSet myMigrationMapSet;
  private JLabel promptLabel;
  private JSeparator mySeparator;
  private JScrollPane myDescriptionScroll;


  public MigrationDialog(Project project, MigrationMapSet migrationMapSet) {
    super(project, true);
    myProject = project;
    myMigrationMapSet = migrationMapSet;
    setTitle("Package and Class Migration");
    setHorizontalStretch(1.2f);
    setOKButtonText("Run");
    init();
  }

  protected Action[] createActions(){
    return new Action[]{getOKAction(),getCancelAction(),getHelpAction()};
  }

  public JComponent getPreferredFocusedComponent() {
    return myMapComboBox;
  }

  protected JComponent createCenterPanel() {
    class MyTextArea extends JTextArea {
      public MyTextArea(String s, int a, int b) {
        super(s, a, b);
        setFocusable(false);
      }
    }

    initMapCombobox();
    myDescriptionTextArea = new MyTextArea("", 3, 40);
    JScrollPane scrollPane = new JScrollPane(myDescriptionTextArea);
    myDescriptionScroll.getViewport().add(myDescriptionTextArea);
    myDescriptionScroll.setBorder(null);
    myDescriptionScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    myDescriptionScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
    myDescriptionTextArea.setEditable(false);
    myDescriptionTextArea.setFont(promptLabel.getFont());
    myDescriptionTextArea.setBackground(myPanel.getBackground());
    myDescriptionTextArea.setLineWrap(true);
    updateDescription();

    myMapComboBox.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent event) {
          updateDescription();
        }
      }
    );

    myEditMapButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent event) {
          editMap();
        }
      }
    );

    myRemoveMapButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent event) {
          removeMap();
        }
      }
    );

    myNewMapButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent event) {
          addNewMap();
        }
      }
    );

    myMapComboBox.registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (myMapComboBox.isPopupVisible()){
            myMapComboBox.setPopupVisible(false);
          }
          else{
            clickDefaultButton();
          }
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
      JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
    );

    return myPanel;
  }

  protected JComponent createNorthPanel() {
    return null;
  }

  private void updateDescription() {
    if (myDescriptionTextArea == null){
      return;
    }
    MigrationMap map = getMigrationMap();
    if (map == null){
      myDescriptionTextArea.setText("");
      return;
    }
    myDescriptionTextArea.setText(map.getDescription());
  }

  private void editMap() {
    MigrationMap oldMap = getMigrationMap();
    if (oldMap == null){
      return;
    }
    MigrationMap newMap = oldMap.cloneMap();
    if (editMap(newMap)){
      if (newMap.getName() == null || newMap.getName().length() == 0 || newMap.getEntryCount() == 0){
        String warningString;
        if (newMap.getEntryCount() == 0){
          warningString = "Migration map is empty.\n";
        }
        else{
          warningString = "Migration map has empty name.\n";
        }
        int result = Messages.showYesNoDialog(
          warningString + "Do you want to delete it?", "Empty Name",
          Messages.getWarningIcon());
        if (result == 0){
          myMigrationMapSet.removeMap(oldMap);
          MigrationMap[] maps = myMigrationMapSet.getMaps();
          initMapCombobox();
          if (maps.length > 0){
            myMapComboBox.setSelectedItem(maps[0]);
          }
          try {
            myMigrationMapSet.saveMaps();
          }
          catch (IOException e) {
            LOG.error("Cannot save migration maps", e);
          }
        }
        return;
      }
      myMigrationMapSet.replaceMap(oldMap, newMap);
      initMapCombobox();
      myMapComboBox.setSelectedItem(newMap);
      try {
        myMigrationMapSet.saveMaps();
      }
      catch (IOException e) {
        LOG.error("Cannot save migration maps", e);
      }
    }
  }

  private boolean editMap(MigrationMap map) {
    if (map == null)
      return false;
    EditMigrationDialog dialog = new EditMigrationDialog(myProject, map);
    dialog.show();
    if (!dialog.isOK())
      return false;
    map.setName(dialog.getName());
    map.setDescription(dialog.getDescription());
    return true;
  }

  private void addNewMap() {
    MigrationMap migrationMap = new MigrationMap();
    if (editMap(migrationMap)){
      myMigrationMapSet.addMap(migrationMap);
      initMapCombobox();
      myMapComboBox.setSelectedItem(migrationMap);
      try {
        myMigrationMapSet.saveMaps();
      }
      catch (IOException e) {
        LOG.error("Cannot save migration maps", e);
      }
    }
  }

  private void removeMap() {
    MigrationMap map = getMigrationMap();
    if (map == null){
      return;
    }
    myMigrationMapSet.removeMap(map);
    MigrationMap[] maps = myMigrationMapSet.getMaps();
    initMapCombobox();
    if (maps.length > 0){
      myMapComboBox.setSelectedItem(maps[0]);
    }
    try {
      myMigrationMapSet.saveMaps();
    }
    catch (IOException e) {
      LOG.error("Cannot save migration maps", e);
    }
  }

  public MigrationMap getMigrationMap() {
    return (MigrationMap)myMapComboBox.getSelectedItem();
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.MIGRATION);
  }

  private void initMapCombobox() {
    if (myMapComboBox.getItemCount() > 0){
      myMapComboBox.removeAllItems();
    }
    MigrationMap[] maps = myMigrationMapSet.getMaps();
    for(int i = 0; i < maps.length; i++){
      myMapComboBox.addItem(maps[i]);
    }
    updateDescription();
  }
}
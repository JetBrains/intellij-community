package com.intellij.openapi.keymap.impl.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.actionSystem.ex.QuickListsManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.impl.KeymapImpl;
import com.intellij.openapi.keymap.impl.KeymapManagerImpl;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ListUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ArrayUtil;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class KeymapPanel extends JPanel {

  private JList myKeymapList;
  private JList myQuickListsList;
  private JList myShortcutsList;

  private DefaultListModel myKeymapListModel = new DefaultListModel();
  private DefaultListModel myQuickListsModel = new DefaultListModel();

  private KeymapImpl mySelectedKeymap;
  private KeymapImpl myActiveKeymap;

  private JButton mySetActiveButton;
  private JButton myCopyButton;
  private JButton myDeleteButton;
  private JButton myAddKeyboardShortcutButton;
  private JButton myAddMouseShortcutButton;
  private JButton myRemoveShortcutButton;
  private JTextField myKeymapNameField;
  private JLabel myBaseKeymapLabel;
  private JLabel myDescriptionLabel;

  private JCheckBox myDisableMnemonicsCheckbox;
  private ActionsTree myActionsTree;
  private final DocumentListener myKeymapNameListener = new DocumentAdapter() {
    public void textChanged(DocumentEvent event) {
      mySelectedKeymap.setName(myKeymapNameField.getText());
      myKeymapList.repaint();
    }
  };

  public KeymapPanel() {
    setLayout(new BorderLayout());
    JPanel headerPanel = new JPanel(new GridLayout(1, 2));
    headerPanel.add(createKeymapListPanel());
    headerPanel.add(createQuickListsPanel());
    add(headerPanel, BorderLayout.NORTH);
    add(createKeymapSettingsPanel(), BorderLayout.CENTER);
  }

  private JPanel createQuickListsPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(IdeBorderFactory.createTitledBorder("Quick lists"));
    panel.setLayout(new BorderLayout());
    myQuickListsList = new JList(myQuickListsModel);
    myQuickListsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myQuickListsList.setCellRenderer(new MyQuickListCellRenderer());

    if (myQuickListsModel.size() > 0) {
      myQuickListsList.setSelectedIndex(0);
    }

    JScrollPane scrollPane = new JScrollPane(myQuickListsList);
    scrollPane.setPreferredSize(new Dimension(180, 100));
    panel.add(scrollPane, BorderLayout.CENTER);
    panel.add(createQuickListButtonsPanel(), BorderLayout.EAST);

    return panel;
  }

  private JPanel createKeymapListPanel() {
    JPanel panel1 = new JPanel();
    panel1.setBorder(IdeBorderFactory.createTitledBorder("Keymaps"));
    JPanel panel = panel1;
    panel.setLayout(new BorderLayout());
    myKeymapList = new JList(myKeymapListModel);
    myKeymapList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myKeymapList.setCellRenderer(new MyKeymapRenderer());
    JScrollPane scrollPane = new JScrollPane(myKeymapList);
    scrollPane.setPreferredSize(new Dimension(180, 100));
    panel.add(scrollPane, BorderLayout.WEST);

    JPanel rightPanel = new JPanel();
    rightPanel.setLayout(new BorderLayout());

    JPanel buttonsPanel = new JPanel();
    buttonsPanel.setLayout(new BorderLayout());
    buttonsPanel.add(createKeymapButtonsPanel(), BorderLayout.NORTH);

    rightPanel.add(buttonsPanel, BorderLayout.WEST);
    panel.add(rightPanel, BorderLayout.CENTER);

    myKeymapList.addListSelectionListener(
      new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          processCurrentKeymapChanged();
        }
      }
    );

    return panel;
  }

  private void processCurrentKeymapChanged() {
    myCopyButton.setEnabled(false);
    myDeleteButton.setEnabled(false);
    mySetActiveButton.setEnabled(false);
    myKeymapNameField.getDocument().removeDocumentListener(myKeymapNameListener);
    myKeymapNameField.setText("");
    myKeymapNameField.setEnabled(false);
    myBaseKeymapLabel.setText("");
    myAddKeyboardShortcutButton.setEnabled(false);
    myAddMouseShortcutButton.setEnabled(false);
    myRemoveShortcutButton.setEnabled(false);

    KeymapImpl selectedKeymap = getSelectedKeymap();
    mySelectedKeymap = selectedKeymap;
    if(selectedKeymap == null) {
      myActionsTree.reset(new KeymapImpl(), getCurrentQuickListIds());
      return;
    }

    myCopyButton.setEnabled(true);
    myKeymapNameField.setText(mySelectedKeymap.getPresentableName());
    myKeymapNameField.getDocument().addDocumentListener(myKeymapNameListener);

    Keymap parent = mySelectedKeymap.getParent();
    if (parent != null) {
      myBaseKeymapLabel.setText("Based on keymap: " + parent.getPresentableName());
    }
    myDisableMnemonicsCheckbox.setSelected(!mySelectedKeymap.areMnemonicsEnabled());
    myDisableMnemonicsCheckbox.setEnabled(mySelectedKeymap.canModify());
    if(mySelectedKeymap.canModify()) {
      myDeleteButton.setEnabled(true);
      myKeymapNameField.setEnabled(true);
      myAddKeyboardShortcutButton.setEnabled(true);
      myAddMouseShortcutButton.setEnabled(true);
      myRemoveShortcutButton.setEnabled(true);
    }
    mySetActiveButton.setEnabled(mySelectedKeymap != myActiveKeymap);

    myActionsTree.reset(mySelectedKeymap, getCurrentQuickListIds());

    updateShortcutsList();
  }

  private QuickList[] getCurrentQuickListIds() {
    int size = myQuickListsModel.size();
    QuickList[] lists = new QuickList[size];
    for (int i = 0; i < lists.length; i++) {
      lists[i] = (QuickList)myQuickListsModel.getElementAt(i);
    }
    return lists;
  }

  private KeymapImpl getSelectedKeymap() {
    return (KeymapImpl)myKeymapList.getSelectedValue();
  }

  private List<Keymap> getAllKeymaps() {
    ListModel model = myKeymapList.getModel();
    List<Keymap> result = new ArrayList<Keymap>();
    for (int i = 0; i < model.getSize(); i++) {
      result.add((Keymap)model.getElementAt(i));
    }
    return result;
  }

  private JPanel createShortcutsPanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    JLabel currentKeysLabel = new JLabel("Shortcuts:");
    currentKeysLabel.setDisplayedMnemonic('u');
    panel.add(currentKeysLabel, new GridBagConstraints(1,0,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.NONE, new Insets(0, 0, 0, 8), 0, 0));

    myShortcutsList = new JList(new DefaultListModel());
    myShortcutsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myShortcutsList.setCellRenderer(new ShortcutListRenderer());
    currentKeysLabel.setLabelFor(myShortcutsList);
    JScrollPane scrollPane = new JScrollPane(myShortcutsList);
    scrollPane.setPreferredSize(new Dimension(160, 200));
    panel.add(scrollPane, new GridBagConstraints(1,1,1,1,1,1,GridBagConstraints.WEST,GridBagConstraints.BOTH, new Insets(0, 0, 0, 8), 0, 0));

    panel.add(
      createShortcutsButtonsPanel(),
      new GridBagConstraints(2,1,1,1,0,0,GridBagConstraints.NORTH,GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0)
    );

    myActionsTree.addListSelectionListener(
      new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          updateShortcutsList();
        }
      }
    );

    return panel;
  }

  private JPanel createQuickListButtonsPanel() {
    JPanel panel = new JPanel(new VerticalFlowLayout());
    final JButton newList = new JButton("Add");
    final JButton editList = new JButton("Edit");
    final JButton removeList = new JButton("Remove");

    newList.setMnemonic('d');
    editList.setMnemonic('E');
    removeList.setMnemonic('v');

    newList.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        QuickList newGroup = new QuickList("unnamed", "", ArrayUtil.EMPTY_STRING_ARRAY, false);
        QuickList edited = editList(newGroup);
        if (edited != null) {
          myQuickListsModel.addElement(edited);
          myQuickListsList.setSelectedIndex(myQuickListsModel.getSize() - 1);
          processCurrentKeymapChanged();
        }
      }
    });

    editList.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        editSelectedQuickList();
      }
    });

    removeList.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        deleteSelectedQuickList();
      }
    });

    myQuickListsList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        boolean enabled = myQuickListsList.getSelectedIndex() >= 0;
        removeList.setEnabled(enabled);
        editList.setEnabled(enabled);
      }
    });

    panel.add(newList);
    panel.add(editList);
    panel.add(removeList);

    editList.setEnabled(false);
    removeList.setEnabled(false);

    return panel;
  }

  private void deleteSelectedQuickList() {
    int idx = myQuickListsList.getSelectedIndex();
    if (idx < 0) return;

    QuickList list = (QuickList)myQuickListsModel.remove(idx);
    list.unregisterAllShortcuts(getAllKeymaps());

    int size = myQuickListsModel.getSize();
    if (size > 0) {
      myQuickListsList.setSelectedIndex(Math.min(idx, size - 1));
    }

    processCurrentKeymapChanged();
  }

  private QuickList editList(QuickList list) {
    List<Keymap> allKeymaps = getAllKeymaps();
    Map<Keymap, ArrayList<Shortcut>> listShortcuts = list.getShortcutMap(allKeymaps);
    list.unregisterAllShortcuts(allKeymaps);

    Project project = (Project)DataManager.getInstance().getDataContext(this).getData(DataConstants.PROJECT);
    EditQuickListDialog dlg = new EditQuickListDialog(project, list, getCurrentQuickListIds());
    dlg.show();

    QuickList editedList = dlg.getList();
    editedList.registerShortcuts(listShortcuts, allKeymaps);

    return dlg.isOK() ? editedList : null;
  }

  private void editSelectedQuickList() {
    QuickList list = (QuickList)myQuickListsList.getSelectedValue();
    if (list == null) return;

    QuickList newList = editList(list);
    if (newList != null) {
      myQuickListsModel.set(myQuickListsList.getSelectedIndex(), newList);
      processCurrentKeymapChanged();
    }
  }

  private JPanel createKeymapButtonsPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
    panel.setLayout(new GridLayout(3, 1, 8, 4));
    mySetActiveButton = new JButton("Set Active");
    mySetActiveButton.setMnemonic('A');
    mySetActiveButton.setMargin(new Insets(2,2,2,2));
    panel.add(mySetActiveButton);
    myCopyButton = new JButton("Copy");
    myCopyButton.setMnemonic('C');
    myCopyButton.setMargin(new Insets(2,2,2,2));
    panel.add(myCopyButton);
    myDeleteButton = new JButton("Delete");
    myDeleteButton.setMnemonic('l');
    myDeleteButton.setMargin(new Insets(2,2,2,2));
    panel.add(myDeleteButton);

    mySetActiveButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          setKeymapActive();
        }
      }
    );

    myCopyButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          copyKeymap();
        }
      }
    );

    myDeleteButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          deleteKeymap();
        }
      }
    );

    return panel;
  }

  private JPanel createKeymapSettingsPanel() {
    JPanel panel1 = new JPanel();
    panel1.setBorder(IdeBorderFactory.createTitledBorder("Keymap Settings"));
    JPanel panel = panel1;
    panel.setLayout(new GridBagLayout());

    panel.add(createKeymapNamePanel(), new GridBagConstraints(0,0,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(5,0,0,0),0,0));

/*
    JLabel actionsLabel = new JLabel("Actions:");
    actionsLabel.setDisplayedMnemonic('t');
    actionsLabel.setHorizontalAlignment(JLabel.LEFT);
    actionsLabel.setHorizontalTextPosition(JLabel.LEFT);
    panel.add(actionsLabel, new GridBagConstraints(0,2,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(5,0,0,0),0,0));
*/

    myActionsTree = new ActionsTree();
    JComponent component = myActionsTree.getComponent();
//    actionsLabel.setLabelFor(component);
    component.setPreferredSize(new Dimension(100, 300));

    panel.add(component, new GridBagConstraints(0,3,1,1,1,1,GridBagConstraints.WEST,GridBagConstraints.BOTH,new Insets(5,0,0,0),0,0));

    panel.add(createShortcutsPanel(), new GridBagConstraints(0,4,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(5,0,0,0),0,0));

    panel.add(createDescriptionPanel(), new GridBagConstraints(0,5,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(5,0,0,0),0,0));

    return panel;
  }

  private JPanel createDescriptionPanel() {
    JPanel panel1 = new JPanel();
    panel1.setBorder(IdeBorderFactory.createTitledBorder("Action Description"));
    JPanel panel = panel1;
    panel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 1;
    myDescriptionLabel = new JLabel(" ");
    panel.add(myDescriptionLabel, gbConstraints);
    return panel;
  }

  private JPanel createKeymapNamePanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    panel.add(new JLabel("Keymap name:"), new GridBagConstraints(0,0,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(0, 0, 0, 8),0,0));

    myKeymapNameField = new JTextField();
    Dimension dimension = new Dimension(150, myKeymapNameField.getPreferredSize().height);
    myKeymapNameField.setPreferredSize(dimension);
    myKeymapNameField.setMinimumSize(dimension);
    panel.add(myKeymapNameField, new GridBagConstraints(1,0,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.NONE,new Insets(0, 8, 0, 0),0,0));

    myBaseKeymapLabel = new JLabel("Parent keymap:");
    Dimension preferredSize = myBaseKeymapLabel.getPreferredSize();
    myBaseKeymapLabel.setPreferredSize(new Dimension(preferredSize.width*2,preferredSize.height));
    panel.add(myBaseKeymapLabel, new GridBagConstraints(2,0,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(0, 16, 0, 8),0,0));

    myDisableMnemonicsCheckbox = new JCheckBox("Disable mnemonics in menu");
    myDisableMnemonicsCheckbox.setMnemonic('M');
    myDisableMnemonicsCheckbox.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        mySelectedKeymap.setDisableMnemonics(myDisableMnemonicsCheckbox.isSelected());
      }
    });
    panel.add(myDisableMnemonicsCheckbox, new GridBagConstraints(3,0,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.NONE,new Insets(0, 0, 0, 0),0,0));

    return panel;
  }

  private JPanel createShortcutsButtonsPanel() {
    JPanel panel = new JPanel(new GridLayout(3, 1, 0, 4));
    panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    myAddKeyboardShortcutButton = new JButton("Add Keyboard Shortcut...");
    myAddKeyboardShortcutButton.setMnemonic('K');
    panel.add(myAddKeyboardShortcutButton);

    myAddMouseShortcutButton=new JButton("Add Mouse Shortcut...");
    myAddMouseShortcutButton.setMnemonic('M');
    panel.add(myAddMouseShortcutButton);

    myRemoveShortcutButton = new JButton("Remove");
    myRemoveShortcutButton.setMnemonic('R');
    panel.add(myRemoveShortcutButton);

    myAddKeyboardShortcutButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          addKeyboardShortcut();
        }
      }
    );

    myAddMouseShortcutButton.addActionListener(
      new ActionListener(){
        public void actionPerformed(ActionEvent e){
          addMouseShortcut();
        }
      }
    );

    myRemoveShortcutButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          removeShortcut();
        }
      }
    );

    return panel;
  }

  private void addKeyboardShortcut() {
    String actionId = myActionsTree.getSelectedActionId();
    if (actionId == null) {
      return;
    }

    ActionsTreeUtil.Group group = myActionsTree.getMainGroup();
    KeyboardShortcutDialog dialog = new KeyboardShortcutDialog(this, actionId, group);

    Shortcut selected = (Shortcut)myShortcutsList.getSelectedValue();
    KeyboardShortcut selectedKeyboardShortcut = selected instanceof KeyboardShortcut ? (KeyboardShortcut)selected : null;

    dialog.setData(mySelectedKeymap, selectedKeyboardShortcut);
    dialog.show();
    if (!dialog.isOK()){
      return;
    }

    KeyboardShortcut keyboardShortcut = dialog.getKeyboardShortcut();

    if (keyboardShortcut == null) return;

    HashMap<String, ArrayList<KeyboardShortcut>> conflicts = mySelectedKeymap.getConflicts(actionId, keyboardShortcut);
    if(conflicts.size() > 0) {
      int result = Messages.showDialog(
        this,
        "The shortcut is already assigned to other actions.\nDo you want to remove other assignments?",
        "Warning",
        new String[]{"Remove", "Leave", "Cancel"},
        0,
        Messages.getWarningIcon());

      if(result == 0) {
        for(Iterator<String> actionIds = conflicts.keySet().iterator(); actionIds.hasNext(); ) {
          String id = actionIds.next();
          ArrayList<KeyboardShortcut> list = conflicts.get(id);
          for (Iterator<KeyboardShortcut> iterator = list.iterator(); iterator.hasNext();) {
            KeyboardShortcut shortcut = iterator.next();
            mySelectedKeymap.removeShortcut(id, shortcut);
          }
        }
      }
      else if (result != 1) {
        return;
      }
    }

    // if shortcut is aleady registered to this action, just select it in the list

    Shortcut[] shortcuts = mySelectedKeymap.getShortcuts(actionId);
    for (int i = 0; i < shortcuts.length; i++) {
      Shortcut shortcut = shortcuts[i];
      if (shortcut.equals(keyboardShortcut)) {
        myShortcutsList.setSelectedIndex(i);
        return;
      }
    }

    mySelectedKeymap.addShortcut(actionId, keyboardShortcut);
    if (StringUtil.startsWithChar(actionId, '$')) {
      mySelectedKeymap.addShortcut("Editor" + actionId.substring(1), keyboardShortcut);
    }
    updateShortcutsList();
    myShortcutsList.setSelectedIndex(myShortcutsList.getModel().getSize()-1);

    repaintLists();
  }

  private void addMouseShortcut(){
    String actionId = myActionsTree.getSelectedActionId();
    if (actionId == null) {
      return;
    }

    Shortcut shortcut = (Shortcut)myShortcutsList.getSelectedValue();
    MouseShortcut mouseShortcut = shortcut instanceof MouseShortcut ? (MouseShortcut)shortcut : null;

    MouseShortcutDialog dialog = new MouseShortcutDialog(
      this,
      mouseShortcut,
      mySelectedKeymap,
      actionId,
      myActionsTree.getMainGroup()
    );
    dialog.show();
    if (!dialog.isOK()){
      return;
    }

    mouseShortcut = dialog.getMouseShortcut();

    if (mouseShortcut == null){
      return;
    }

    String[] actionIds = mySelectedKeymap.getActionIds(mouseShortcut);
    if(actionIds.length > 1 || (actionIds.length == 1 && !actionId.equals(actionIds[0]))) {
      int result = Messages.showDialog(
        this,
        "The shortcut is already assigned to other actions.\nDo you want to remove other assignments?",
        "Warning",
        new String[]{"Remove", "Leave", "Cancel"},
        0,
        Messages.getWarningIcon());

      if(result == 0) {
        for(int i = 0; i < actionIds.length; i++) {
          String id = actionIds[i];
          mySelectedKeymap.removeShortcut(id, mouseShortcut);
        }
      }
      else if (result != 1) {
        return;
      }
    }

    // if shortcut is aleady registered to this action, just select it in the list

    Shortcut[] shortcuts = mySelectedKeymap.getShortcuts(actionId);
    for (int i = 0; i < shortcuts.length; i++) {
      if (shortcuts[i].equals(mouseShortcut)) {
        myShortcutsList.setSelectedIndex(i);
        return;
      }
    }

    mySelectedKeymap.addShortcut(actionId, mouseShortcut);
    if (StringUtil.startsWithChar(actionId, '$')) {
      mySelectedKeymap.addShortcut("Editor" + actionId.substring(1), mouseShortcut);
    }
    updateShortcutsList();
    myShortcutsList.setSelectedIndex(myShortcutsList.getModel().getSize()-1);

    repaintLists();
  }

  private void repaintLists() {
    myActionsTree.getComponent().repaint();
    myKeymapList.repaint();
  }

  private void removeShortcut() {
    String actionId = myActionsTree.getSelectedActionId();
    if (actionId == null) {
      return;
    }
    Shortcut shortcut = (Shortcut)myShortcutsList.getSelectedValue();
    if(shortcut == null) {
      return;
    }
    int selectedIndex = myShortcutsList.getSelectedIndex();
    mySelectedKeymap.removeShortcut(actionId, shortcut);
    if (StringUtil.startsWithChar(actionId, '$')) {
      mySelectedKeymap.removeShortcut("Editor" + actionId.substring(1), shortcut);
    }

    updateShortcutsList();

    int count = myShortcutsList.getModel().getSize();
    if(count > 0) {
      myShortcutsList.setSelectedIndex(Math.max(selectedIndex-1, 0));
    }
    else {
      myShortcutsList.clearSelection();
    }

    repaintLists();
  }

  private void setKeymapActive() {
    KeymapImpl keymap = getSelectedKeymap();
    if(keymap != null) {
      myActiveKeymap = keymap;
    }
    myKeymapList.repaint();
    processCurrentKeymapChanged();
  }

  private void copyKeymap() {
    KeymapImpl keymap = getSelectedKeymap();
    if(keymap == null) {
      return;
    }
    KeymapImpl newKeymap = keymap.deriveKeymap();

    String newKeymapName = "unnamed";
    if(!tryNewKeymapName(newKeymapName)) {
      for(int i=0; ; i++) {
        newKeymapName = "unnamed"+i;
        if(tryNewKeymapName(newKeymapName)) {
          break;
        }
      }
    }
    newKeymap.setName(newKeymapName);
    newKeymap.setCanModify(true);
    myKeymapListModel.addElement(newKeymap);
    myKeymapList.setSelectedValue(newKeymap, true);
    processCurrentKeymapChanged();

    int result = Messages.showYesNoDialog(this, "Make the new keymap active?", "New Keymap", Messages.getQuestionIcon());
    if(result == 0) {
      myActiveKeymap = newKeymap;
      myKeymapList.repaint();
    }

    if (myKeymapNameField.isEnabled()) {
      myKeymapNameField.setSelectionStart(0);
      myKeymapNameField.setSelectionEnd(myKeymapNameField.getText().length());
      myKeymapNameField.requestFocus();

    }
  }

  private boolean tryNewKeymapName(String name) {
    for(int i=0; i<myKeymapListModel.size(); i++) {
      Keymap k = (Keymap)myKeymapListModel.getElementAt(i);
      if(name.equals(k.getName())) {
        return false;
      }
    }

    return true;
  }

  private void deleteKeymap() {
    Keymap keymap = getSelectedKeymap();
    if(keymap == null) {
      return;
    }
    int result = Messages.showYesNoDialog(this, "Do you want to delete the keymap?", "Warning", Messages.getWarningIcon());
    if (result != 0) {
      return;
    }
    ListUtil.removeSelectedItems(myKeymapList);
    int count = myKeymapListModel.getSize();
    if(count >= 0) {
      if (myActiveKeymap == keymap) {
        myActiveKeymap = (KeymapImpl)myKeymapListModel.getElementAt(0);
      }
    }
    else {
      myActiveKeymap = null;
    }
    processCurrentKeymapChanged();
    myKeymapList.repaint();
  }


  private void updateShortcutsList() {
    DefaultListModel shortcutsModel = (DefaultListModel)myShortcutsList.getModel();
    shortcutsModel.clear();
    String actionId = myActionsTree.getSelectedActionId();
    myDescriptionLabel.setText(" ");
    if (actionId != null && mySelectedKeymap != null) {
      AnAction action = ActionManager.getInstance().getAction(actionId);
      if (action != null) {
        String description = action.getTemplatePresentation().getDescription();
        if (description != null && description.trim().length() > 0) {
          myDescriptionLabel.setText(description);
        }
      }
      else {
        QuickList list = myActionsTree.getSelectedQuickList();
        if (list != null) {
          String description = list.getDescription().trim();
          if (description.length() > 0) {
            myDescriptionLabel.setText(description);
          }
        }
      }

      Shortcut[] shortcuts = mySelectedKeymap.getShortcuts(actionId);
      for(int i = 0; i < shortcuts.length; i++){
        shortcutsModel.addElement(shortcuts[i]);
      }
      if(shortcutsModel.size() > 0) {
        myShortcutsList.setSelectedIndex(0);
      }

      myAddKeyboardShortcutButton.setEnabled(mySelectedKeymap.canModify());
      myAddMouseShortcutButton.setEnabled(mySelectedKeymap.canModify());
      myRemoveShortcutButton.setEnabled(mySelectedKeymap.canModify() && shortcutsModel.size() > 0);
    }
    else {
      myAddKeyboardShortcutButton.setEnabled(false);
      myAddMouseShortcutButton.setEnabled(false);
      myRemoveShortcutButton.setEnabled(false);
    }
  }

  private final class MyKeymapRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean selected, boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, selected, cellHasFocus);
      Keymap keymap = (Keymap)value;

      // Set color and font.

      Font font = getFont();
      if(keymap == myActiveKeymap) {
        font = font.deriveFont(Font.BOLD);
      }
      setFont(font);
      if(selected){
        setForeground(UIManager.getColor("List.selectionForeground"));
      }else{
        if(keymap.canModify()){
          setForeground(UIManager.getColor("List.foreground"));
        }else{
          setForeground(Color.GRAY);
        }
      }

      // Set text.

      String name = keymap.getPresentableName();
      if(name == null) {
        name = "<unnamed>";
      }
      if(keymap == myActiveKeymap) {
        name += " (active)";
      }
      setText(name);
      return this;
    }
  }

  private static final class ShortcutListRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      Shortcut shortcut = (Shortcut)value;
      setText(KeymapUtil.getShortcutText(shortcut));
      setIcon(KeymapUtil.getShortcutIcon(shortcut));
      return this;
    }
  }

  public void reset() {
    myKeymapListModel.removeAllElements();
    KeymapManagerEx keymapManager = KeymapManagerEx.getInstanceEx();
    Keymap[] keymaps = keymapManager.getAllKeymaps();
    for(int i = 0; i < keymaps.length; i++){
      KeymapImpl keymap = (KeymapImpl)keymaps[i];
      if(keymap.canModify()) {
        keymap = keymap.copy();
      }
      myKeymapListModel.addElement(keymap);
      if(keymapManager.getActiveKeymap() == keymaps[i]) {
        myActiveKeymap = keymap;
      }
    }

    if(myKeymapListModel.getSize() == 0) {
      KeymapImpl keymap = new KeymapImpl();
      keymap.setName("<No name>");
      myKeymapListModel.addElement(keymap);
    }

    myActionsTree.reset(myActiveKeymap, QuickListsManager.getInstance().getAllQuickLists());

    myQuickListsModel.removeAllElements();
    QuickList[] allQuickLists = QuickListsManager.getInstance().getAllQuickLists();
    for (int i = 0; i < allQuickLists.length; i++) {
      QuickList list = allQuickLists[i];
      myQuickListsModel.addElement(list);
    }

    mySelectedKeymap = (KeymapImpl)myKeymapListModel.elementAt(0);
    myKeymapList.setSelectedValue(myActiveKeymap, true);
    processCurrentKeymapChanged();
  }

  public void apply() throws ConfigurationException{
    HashSet keymapNames = new HashSet();
    for(int i = 0; i < myKeymapListModel.getSize(); i++){
      Keymap keymap = (Keymap)myKeymapListModel.elementAt(i);
      String name = keymap.getName();
      if (keymapNames.contains(name)) {
        throw new ConfigurationException("All keymaps should have unique names");
      }
      keymapNames.add(name);
    }

    KeymapManagerImpl keymapManager = (KeymapManagerImpl)KeymapManager.getInstance();
    keymapManager.removeAllKeymapsExceptUnmodifiable();
    for(int i = 0; i < myKeymapListModel.getSize(); i++){
      Keymap keymap = (Keymap)myKeymapListModel.elementAt(i);
      if(keymap.canModify()) {
        keymapManager.addKeymap(keymap);
      }
    }
    keymapManager.setActiveKeymap(myActiveKeymap);
    try {
      keymapManager.save();
    }
    catch (IOException e) {
      throw new ConfigurationException(e.getMessage());
    }

    QuickListsManager.getInstance().removeAllQuickLists();
    int size = myQuickListsModel.getSize();
    for (int i = 0; i < size; i++) {
      QuickList list = (QuickList)myQuickListsModel.getElementAt(i);
      QuickListsManager.getInstance().registerQuickList(list, false);
    }

    QuickListsManager.getInstance().registerActions();
  }

  boolean isModified() {
    KeymapManagerEx keymapManager = KeymapManagerEx.getInstanceEx();
    if (!Comparing.equal(myActiveKeymap, keymapManager.getActiveKeymap())) {
      return true;
    }
    Keymap[] managerKeymaps = keymapManager.getAllKeymaps();
    Keymap[] panelKeymaps = new Keymap[myKeymapListModel.getSize()];
    for(int i = 0; i < myKeymapListModel.getSize(); i++){
      panelKeymaps[i] = (Keymap)myKeymapListModel.elementAt(i);
    }

    if (!Comparing.equal(managerKeymaps, panelKeymaps)) return true;
    QuickList[] storedLists = QuickListsManager.getInstance().getAllQuickLists();

    QuickList[] modelLists = new QuickList[myQuickListsModel.getSize()];
    for (int i = 0; i < modelLists.length; i++) {
      modelLists[i] = (QuickList)myQuickListsModel.getElementAt(i);
    }

    return !Comparing.equal(storedLists, modelLists);
  }

  public Dimension getPreferredSize() {
    //TODO[anton]: it's a hack!!!
    Dimension preferredSize = super.getPreferredSize();
    if (preferredSize.height > 600) {
      preferredSize.height = 600;
    }
    return preferredSize;
  }

  public void selectAction(String actionId) {
    myActionsTree.selectAction(actionId);
  }

  private static class MyQuickListCellRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list,
                                                  Object value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      QuickList quickList = (QuickList)value;
      setText(quickList.getDisplayName());
      return this;
    }
  }
}
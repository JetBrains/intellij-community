package com.intellij.openapi.fileTypes.impl;

import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.ide.highlighter.custom.impl.CustomFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.UserFileType;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.ui.ListUtil;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Belyaev
 */
public class FileTypeConfigurable extends BaseConfigurable implements ApplicationComponent {
  private RecognizedFileTypes myRecognizedFileType;
  private ExtensionsPanel myExtensions;
  private FileTypePanel myFileTypePanel;
  private HashSet<FileType> myTempFileTypes;
  private FileTypeManagerImpl myManager;
  private Map<String, FileType> myTempExtension2TypeMap;
  private Map<UserFileType, UserFileType> myOriginalToEditedMap = new HashMap<UserFileType, UserFileType>();

  public FileTypeConfigurable(FileTypeManager fileTypeManager) {
    myManager = (FileTypeManagerImpl)fileTypeManager;
  }

  public void disposeComponent() {
  }

  public void initComponent() {
  }

  public String getDisplayName() {
    return "File Types";
  }

  public JComponent createComponent() {
    myFileTypePanel = new FileTypePanel();
    myRecognizedFileType = myFileTypePanel.myRecognizedFileType;
    myExtensions = myFileTypePanel.myExtensions;
    myRecognizedFileType.attachActions(this);
    myRecognizedFileType.myFileTypesList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        updateExtensionList();
      }
    });
    myExtensions.attachActions(this);
    return myFileTypePanel.getComponent();
  }

  private void updateFileTypeList() {
    FileType[] types = myTempFileTypes.toArray(new FileType[myTempFileTypes.size()]);
    Arrays.sort(types, new Comparator() {
      public int compare(Object o1, Object o2) {
        FileType fileType1 = (FileType)o1;
        FileType fileType2 = (FileType)o2;
        return fileType1.getDescription().compareToIgnoreCase(fileType2.getDescription());
      }
    });
    myRecognizedFileType.setFileTypes(types);
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableFileTypes.png");
  }

  private static FileType[] getModifiableFileTypes() {
    FileType[] registeredFileTypes = FileTypeManager.getInstance().getRegisteredFileTypes();
    ArrayList<FileType> result = new ArrayList<FileType>();
    for (FileType fileType : registeredFileTypes) {
      if (!fileType.isReadOnly()) result.add(fileType);
    }
    return result.toArray(new FileType[result.size()]);
  }

  public void apply() throws ConfigurationException {
    Set<UserFileType> modifiedUserTypes = myOriginalToEditedMap.keySet();
    for (UserFileType oldType : modifiedUserTypes) {
      UserFileType newType = myOriginalToEditedMap.get(oldType);
      oldType.copyFrom(newType);
    }
    myOriginalToEditedMap.clear();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        myManager.setExtensionMap(myTempFileTypes, myTempExtension2TypeMap);
      }
    });
  }

  public void reset() {
    myTempExtension2TypeMap = new HashMap<String, FileType>(myManager.getExtensionMap());
    myTempFileTypes = new HashSet<FileType>(Arrays.asList(getModifiableFileTypes()));
    myOriginalToEditedMap.clear();

    updateFileTypeList();
    updateExtensionList();
  }

  public boolean isModified() {
    HashSet types = new HashSet(Arrays.asList(getModifiableFileTypes()));
    return !myTempExtension2TypeMap.equals(myManager.getExtensionMap()) ||
           !myTempFileTypes.equals(types) ||
           !myOriginalToEditedMap.isEmpty();
  }

  public void disposeUIResources() {
    if (myFileTypePanel != null) myFileTypePanel.dispose();
    myFileTypePanel = null;
    myRecognizedFileType = null;
    myExtensions = null;
  }

  private static class ExtensionRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      setText(" " + getText());
      return this;
    }

    public Dimension getPreferredSize() {
      return new Dimension(0, 20);
    }
  }

  private void updateExtensionList() {
    FileType type = myRecognizedFileType.getSelectedFileType();
    if (type == null) return;
    List<String> extensions = new ArrayList<String>();
    for (final String extension : myTempExtension2TypeMap.keySet()) {
      if (type.equals(myTempExtension2TypeMap.get(extension))) {
        extensions.add(extension);
      }
    }

    myExtensions.clearList();
    Collections.sort(extensions);    
    for (String extension : extensions) {
      myExtensions.addExtension(extension);
    }
    myExtensions.ensureSelectionExists();
  }

  private void editFileType() {
    FileType fileType = myRecognizedFileType.getSelectedFileType();
    if (!canBeModified(fileType)) return;
    UserFileType ftToEdit = myOriginalToEditedMap.get(fileType);
    if (ftToEdit == null) ftToEdit = ((UserFileType)fileType).clone();
    if (ftToEdit.getEditor() == null) return;
    TypeEditor editor = new TypeEditor(myRecognizedFileType.myEditButton, ftToEdit, "Edit File Type");
    editor.show();
    if (editor.isOK()) {
      myOriginalToEditedMap.put((UserFileType)fileType, ftToEdit);
    }
  }

  private void removeFileType() {
    FileType fileType = myRecognizedFileType.getSelectedFileType();
    if (fileType == null) return;
    myTempFileTypes.remove(fileType);
    myOriginalToEditedMap.remove(fileType);

    Iterator iterator = myTempExtension2TypeMap.keySet().iterator();
    while (iterator.hasNext()) {
      String extension = (String)iterator.next();
      FileType t = myTempExtension2TypeMap.get(extension);
      if (fileType == t) {
        iterator.remove();
      }
    }

    updateFileTypeList();
    updateExtensionList();
  }

  private static boolean canBeModified(FileType fileType) {
    return fileType instanceof UserFileType && ((UserFileType)fileType).getEditor() != null;
  }

  private void addFileType() {
    //TODO: support adding binary file types...
    CustomFileType type = new CustomFileType(new SyntaxTable());
    TypeEditor editor = new TypeEditor(myRecognizedFileType.myAddButton, type, "New File Type");
    editor.show();
    if (editor.isOK()) {
      myTempFileTypes.add(type);
      updateFileTypeList();
      updateExtensionList();
      myRecognizedFileType.selectFileType(type);
      type.initSupport();
    }
  }

  private void addExtension() {
    FileType type = myRecognizedFileType.getSelectedFileType();
    if (type == null) return;
    String text = Messages.showInputDialog(myExtensions.myAddButton, "Enter new extension:", "Add Extension", Messages.getQuestionIcon());
    if (text == null || "".equals(text)) return;

    FileType registeredFileType = addNewExtension(type, text);
    if (registeredFileType != null) {
      Messages.showMessageDialog(myExtensions.myAddButton,
                                 "This extension is already registered by \'" + registeredFileType.getDescription() + "\' filetype",
                                 "Add Extension",
                                 Messages.getErrorIcon());
    }
  }

  public FileType addNewExtension(FileType type, String extension) {
    extension = extension.toLowerCase();
    if (StringUtil.startsWithChar(extension, '.')) extension = extension.substring(1);
    FileType fileTypeByExtension = myTempExtension2TypeMap.get(extension);

    if (fileTypeByExtension != null && fileTypeByExtension != StdFileTypes.UNKNOWN) {
      return fileTypeByExtension;
    }
    FileType registeredFileType = FileTypeManager.getInstance().getFileTypeByExtension(extension);
    if (registeredFileType != StdFileTypes.UNKNOWN && registeredFileType.isReadOnly()) {
      return registeredFileType;
    }

    if (extension != null) {
      myTempExtension2TypeMap.put(extension, type);
      myExtensions.addExtensionAndSelect(extension);
    }
    myExtensions.myExtensionsList.requestFocus();
    return null;
  }

  private void removeExtension() {
    FileType type = myRecognizedFileType.getSelectedFileType();
    if (type == null) return;
    String extension = myExtensions.removeSelected();
    if (extension == null) return;
    myTempExtension2TypeMap.remove(extension);
    myExtensions.myExtensionsList.requestFocus();
  }

  public String getHelpTopic() {
    return "preferences.fileTypes";
  }

  public String getComponentName() {
    return "FileTypeConfigurable";
  }

  public static class RecognizedFileTypes extends JPanel {
    private JList myFileTypesList;
    private JButton myAddButton;
    private JButton myEditButton;
    private JButton myRemoveButton;
    private JPanel myWholePanel;

    public RecognizedFileTypes() {
      super(new BorderLayout());
      add(myWholePanel, BorderLayout.CENTER);
      myFileTypesList.setCellRenderer(new FileTypeRenderer());
      myFileTypesList.setModel(new DefaultListModel());
    }

    public void attachActions(final FileTypeConfigurable controller) {
      myAddButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          controller.addFileType();
        }
      });
      myEditButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          controller.editFileType();
        }
      });
      myFileTypesList.addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          FileType fileType = getSelectedFileType();
          boolean b = canBeModified(fileType);
          myEditButton.setEnabled(b);
          myRemoveButton.setEnabled(b);
        }
      });
      myRemoveButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          controller.removeFileType();
        }
      });
      myFileTypesList.addMouseListener(new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() == 2) controller.editFileType();
        }
      });
    }

    public FileType getSelectedFileType() {
      return (FileType)myFileTypesList.getSelectedValue();
    }

    public JComponent getComponent() {
      return myWholePanel;
    }

    public void setFileTypes(FileType[] types) {
      DefaultListModel listModel = (DefaultListModel)myFileTypesList.getModel();
      listModel.clear();
      for (FileType type : types) {
        if (type != StdFileTypes.UNKNOWN) {
          listModel.addElement(type);
        }
      }
      ListScrollingUtil.ensureSelectionExists(myFileTypesList);
    }

    public int getSelectedIndex() {
      return myFileTypesList.getSelectedIndex();
    }

    public void setSelectionIndex(int selectedIndex) {
      myFileTypesList.setSelectedIndex(selectedIndex);
    }

    public void selectFileType(FileType fileType) {
      myFileTypesList.setSelectedValue(fileType, true);
      myFileTypesList.requestFocus();
    }
  }

  public static class ExtensionsPanel extends JPanel {
    private JList myExtensionsList;
    private JButton myAddButton;
    private JButton myRemoveButton;
    private JPanel myWholePanel;

    public ExtensionsPanel() {
      super(new BorderLayout());
      add(myWholePanel, BorderLayout.CENTER);
      myExtensionsList.setCellRenderer(new ExtensionRenderer());
      myExtensionsList.setModel(new DefaultListModel());
      myExtensionsList.addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          myRemoveButton.setEnabled(myExtensionsList.getSelectedIndex() != -1 && getListModel().size() > 0);
        }
      });
    }

    public void attachActions(final FileTypeConfigurable controller) {
      myAddButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          controller.addExtension();
        }
      });

      myRemoveButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          controller.removeExtension();
        }
      });

    }

    public JComponent getComponent() {
      return myWholePanel;
    }

    public void clearList() {
      getListModel().clear();
      myExtensionsList.clearSelection();
    }

    private DefaultListModel getListModel() {
      return (DefaultListModel)myExtensionsList.getModel();
    }

    public void addExtension(String extension) {
      getListModel().addElement(extension);
    }

    public void ensureSelectionExists() {
      ListScrollingUtil.ensureSelectionExists(myExtensionsList);
    }

    public void addExtensionAndSelect(String extension) {
      addExtension(extension);
      ListScrollingUtil.selectItem(myExtensionsList, getListModel().getSize() - 1);
    }

    public boolean isListEmpty() {
      return getListModel().size() == 0;
    }

    public String removeSelected() {
      Object selectedValue = myExtensionsList.getSelectedValue();
      if (selectedValue == null) return null;
      ListUtil.removeSelectedItems(myExtensionsList);
      return (String)selectedValue;
    }

    public String getDefaultExtension() {
      return (String)getListModel().getElementAt(0);
    }
  }

  private static class FileTypePanel {
    private JPanel myWholePanel;
    private RecognizedFileTypes myRecognizedFileType;
    private ExtensionsPanel myExtensions;

    public JComponent getComponent() {
      return myWholePanel;
    }

    public void dispose() {
      myRecognizedFileType.setFileTypes(FileType.EMPTY_ARRAY);
      myExtensions.clearList();
    }
  }

  private static class TypeEditor<T extends UserFileType<T>> extends DialogWrapper {
    private T myFileType;
    private SettingsEditor<T> myEditor;

    public TypeEditor(Component parent, T fileType, final String title) {
      super(parent, false);
      myFileType = fileType;
      myEditor = fileType.getEditor();
      setTitle(title);
      init();
    }

    protected void init() {
      super.init();
      myEditor.resetFrom(myFileType);
    }

    protected JComponent createCenterPanel() {
      return myEditor.getComponent();
    }

    protected void doOKAction() {
      try {
        myEditor.applyTo(myFileType);
      }
      catch (ConfigurationException e) {
        Messages.showErrorDialog(getContentPane(), e.getMessage(), e.getTitle());
        return;
      }
      super.doOKAction();
    }
  }
}
package com.intellij.openapi.fileTypes.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.impl.FileTypeRenderer;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ListScrollingUtil;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Comparator;

public class FileTypeChooser extends DialogWrapper{
  private DefaultListModel myModel = new DefaultListModel();
  private JList myList = new JList(myModel);
  private String myExtension;

  public FileTypeChooser(String extension) {
    super(true);
    myExtension=extension;

    FileType[] fileTypes = FileTypeManager.getInstance().getRegisteredFileTypes();
    Arrays.sort(fileTypes, new Comparator<FileType>() {
      public int compare(final FileType fileType1, final FileType fileType2) {
        if (fileType1 == null){
          return 1;
        }
        if (fileType2 == null){
          return -1;
        }
        return fileType1.getDescription().compareToIgnoreCase(fileType2.getDescription());  
      }
    });
    for(int i = 0; i < fileTypes.length; i++){
      FileType type = fileTypes[i];
      if (!type.isReadOnly() && type != StdFileTypes.UNKNOWN) {
        myModel.addElement(type);
      }
    }
    setTitle("Register New Extension");
    init();
  }

  protected JComponent createCenterPanel(){
    JPanel panel=new JPanel(new GridBagLayout());
    JLabel label = new JLabel("The extension '" + myExtension + "' is not associated with a registered file type. Please choose one:");
    label.setUI(new MultiLineLabelUI());
    panel.add(
      label,
      new GridBagConstraints(0,0,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(0,0,5,5),0,0)
    );

    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.setCellRenderer(new FileTypeRenderer());

    myList.addMouseListener(
      new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() == 2){
            doOKAction();
          }
        }
      }
    );

    myList.getSelectionModel().addListSelectionListener(
      new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          updateButtonsState();
        }
      }
    );


    JScrollPane scrollPane = new JScrollPane(myList);
    scrollPane.setPreferredSize(new Dimension(150, 200));
    ListScrollingUtil.selectItem(myList, StdFileTypes.PLAIN_TEXT);
    panel.add(
      scrollPane,
      new GridBagConstraints(0,1,1,1,1,1,GridBagConstraints.CENTER,GridBagConstraints.BOTH,new Insets(0,0,0,0),0,0)
    );
    return panel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myList;
  }

  private void updateButtonsState() {
    setOKActionEnabled(myList.getSelectedIndex() != -1);
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.fileTypes.FileTypeChooser";
  }

  public FileType getSelectedType() {
    return (FileType)myList.getSelectedValue();
  }

  /**
   * If fileName is already associated any known file type returns it.
   * Otherwise asks user to select file type and associates it with fileName extension if any selected.
   * @return Known file type or null. Never returns {@link com.intellij.openapi.fileTypes.StdFileTypes#UNKNOWN}.
   */
  public static FileType getKnownFileTypeOrAssociate(VirtualFile file) {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    FileType type = fileTypeManager.getFileTypeByFile(file);
    if (type == StdFileTypes.UNKNOWN)
      type = getKnownFileTypeOrAssociate(file.getName());
    return type;
  }
  public static FileType getKnownFileTypeOrAssociate(String fileName) {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    FileType type = fileTypeManager.getFileTypeByFileName(fileName);
    if (type == StdFileTypes.UNKNOWN)
      type = associateFileType(fileName);
    return type;
  }

  public static FileType associateFileType(String fileName) {
    final String extension = ((FileTypeManagerEx) FileTypeManager.getInstance()).getExtension(fileName);
    FileTypeChooser chooser = new FileTypeChooser(extension);
    chooser.show();
    if (!chooser.isOK()) return null;
    final FileType type = chooser.getSelectedType();
    if (type == StdFileTypes.UNKNOWN) return null;
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        FileTypeManagerEx.getInstanceEx().associateExtension(type, extension);
      }
    });
    return type;
  }
}

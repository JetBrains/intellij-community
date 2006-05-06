package com.intellij.openapi.fileTypes.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypesBundle;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.impl.FileTypeRenderer;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ListScrollingUtil;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Comparator;

public class FileTypeChooser extends DialogWrapper{
  private DefaultListModel myModel = new DefaultListModel();
  private JList myList;
  private JLabel myTitleLabel;
  private JTextField myPattern;
  private JPanel myPanel;
  private final String myFileName;

  private FileTypeChooser(String pattern, String fileName) {
    super(true);
    myFileName = fileName;

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

    for (FileType type : fileTypes) {
      if (!type.isReadOnly() && type != StdFileTypes.UNKNOWN) {
        myModel.addElement(type);
      }
    }

    myList.setModel(myModel);
    myPattern.setText(pattern);

    setTitle(FileTypesBundle.message("filetype.chooser.title"));
    init();
  }

  protected JComponent createCenterPanel() {
    myTitleLabel.setText(FileTypesBundle.message("filetype.chooser.prompt", myFileName));

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

    ListScrollingUtil.selectItem(myList, StdFileTypes.PLAIN_TEXT);

    return myPanel;
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
    final FileTypeChooser chooser = new FileTypeChooser(suggestPatternText(fileName), fileName);
    chooser.show();
    if (!chooser.isOK()) return null;
    final FileType type = chooser.getSelectedType();
    if (type == StdFileTypes.UNKNOWN) return null;

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        FileTypeManagerEx.getInstanceEx().associatePattern(type, chooser.myPattern.getText());
      }
    });

    return type;
  }

  private static String suggestPatternText(final String fileName) {
    String pattern = FileUtil.getExtension(fileName);

    final String finalPattern;
    if (StringUtil.isEmpty(pattern)) {
      finalPattern = fileName;
    }
    else {
      finalPattern = "*." + pattern;
    }
    return finalPattern;
  }
}

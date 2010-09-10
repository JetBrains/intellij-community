package myDocumentFiles;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Chursin
 * Date: Aug 30, 2010
 * Time: 1:42:38 PM
 */
public class MyVisualPanel extends DialogWrapper {
    private JTabbedPane viewFileTab;
    private JTextField fileName;
    private JButton buttonBrowse;
    private JTextField directoryName;
    private JFileChooser fileChooser = new JFileChooser("");
    private VirtualFile selectedVFile;
    private Document docFile;
    // Set maximum allowed number of lines in a text file to edit.
    private final int maxNumberofLines = 250;


    private JPanel myVisualUI;
    private JEditorPane myFileEditor;
    private JPanel viewFilePanel;
    private String initialFileContent = null;
    private boolean nFocus;


    public MyVisualPanel(boolean canBeParent) {
        super(canBeParent);
        init();


        // The Browse button listener.
        buttonBrowse.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {

                if (OpenFile()) {
                    if (docFile.getLineCount() > maxNumberofLines) {
                        Messages.showMessageDialog("File too long! Maximum allowed number of lines: " +
                                String.valueOf(maxNumberofLines) + ".\n"+
                                "To change this setting, modify the MyVisualPanel.maxNumberofLines field.", "Error", Messages.getErrorIcon());
                        return;
                    }
                    // Read the text file content.
                    initialFileContent = docFile.getText();
                    // Enable the View File tab
                    viewFileTab.setEnabled(true);
                    // Fill the "File name" and "Directory" text fields.
                    fileName.setText(selectedVFile.getName());
                    directoryName.setText(selectedVFile.getParent().getUrl());
                    myFileEditor.setText(initialFileContent);
                }


            }
        });

        // The file editor focus listener.
        myFileEditor.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                if (!docFile.isWritable() && nFocus) {
                    nFocus = false;
                    Messages.showMessageDialog("This file is read-only. You cannot save your changes.", "Warning",
                            Messages.getWarningIcon());
                    return;

                } else return;
            }
        });
    }

    // Display the Open dialog and open the selected file.
    public boolean OpenFile() {
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Text files", "txt");
        fileChooser.setFileFilter(filter);
        //... Open a file dialog.
        int retval = fileChooser.showOpenDialog(null);
        if (retval == JFileChooser.APPROVE_OPTION) {
            // Get virtual file
            selectedVFile = LocalFileSystem.getInstance().findFileByIoFile(fileChooser.getSelectedFile());
            // Get document file
            docFile = FileDocumentManager.getInstance().getDocument(selectedVFile);
            nFocus = true;

            return true;
        }
        viewFilePanel.setEnabled(false);
        return false;
    }


    public JComponent createCenterPanel() {

        return (JComponent) myVisualUI;

    }
      // The OK button handler.
    protected void doOKAction() {
        if (initialFileContent == null) {
            this.close(0);
            return;
        }
        if (!initialFileContent.equals(myFileEditor.getText())) {
            if (Messages.showYesNoDialog("The file " + selectedVFile.getName() +
                    " has been changed. Are you sure you want to overwrite it?", "File Changed", Messages.getQuestionIcon()) == 0) {
                if (docFile.isWritable()) {
                    docFile.setText(myFileEditor.getText());
                } else {
                    Messages.showMessageDialog("This file is read-only! You cannot save your changes.", "Error", Messages.getErrorIcon());
                    return;
                }


            } else return;
        }

        this.close(0);
        this.dispose();


    }

}

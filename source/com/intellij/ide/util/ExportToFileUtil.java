package com.intellij.ide.util;

import com.intellij.ide.ExporterToTextFile;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.CommonBundle;
import com.intellij.util.SystemProperties;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.TooManyListenersException;

public class ExportToFileUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.ExportToFileUtil");

  public static void exportTextToFile(Project project, String fileName, String textToExport) {
    String prepend = "";
    File file = new File(fileName);
    if (file.exists()) {
      int result = Messages.showDialog(
        project,
        IdeBundle.message("error.text.file.already.exists", fileName),
        IdeBundle.message("title.warning"),
          new String[]{
            IdeBundle.message("action.overwrite"),
            IdeBundle.message("action.append"),
            CommonBundle.getCancelButtonText()
          },
        0,
        Messages.getWarningIcon()
      );

      if (result != 1 && result != 0) {
        return;
      }
      if (result == 1) {
        char[] buf = new char[(int)file.length()];
        try {
          FileReader reader = new FileReader(fileName);
          try {
            reader.read(buf, 0, (int)file.length());
            prepend = new String(buf) + SystemProperties.getLineSeparator();
          }
          finally {
            reader.close();
          }
        }
        catch (IOException e) {
        }
      }
    }

    try {
      FileWriter writer = new FileWriter(fileName);
      try {
        writer.write(prepend + textToExport);
      }
      finally {
        writer.close();
      }
    }
    catch (IOException e) {
      Messages.showMessageDialog(
        project,
        IdeBundle.message("error.writing.to.file", fileName),
        CommonBundle.getErrorTitle(),
        Messages.getErrorIcon()
      );
    }
  }

  public static class ExportDialogBase extends DialogWrapper {
    private Project myProject;
    private ExporterToTextFile myExporter;
    protected JTextArea myTextArea;
    protected JScrollPane myTextScrollPane;
    protected JTextField myTfFile;
    protected JButton myFileButton;
    private ChangeListener myListener;

    public ExportDialogBase(Project project, ExporterToTextFile exporter) {
      super(project, true);
      myProject = project;
      myExporter = exporter;

      myTfFile = new JTextField();
      myFileButton = new FixedSizeButton(myTfFile);

      setHorizontalStretch(1.5f);
      setTitle(IdeBundle.message("title.export.preview"));
      setOKButtonText(IdeBundle.message("button.save"));
      setButtonsMargin(null);
      init();
      try {
        myListener = new ChangeListener() {
          public void stateChanged(ChangeEvent e) {
            initText();
          }
        };
        myExporter.addSettingsChangedListener(myListener);
      }
      catch (TooManyListenersException e) {
        LOG.error(e);
      }
      initText();
    }

    public void dispose() {
      myExporter.removeSettingsChangedListener(myListener);
      super.dispose();
    }

    private void initText() {
      myTextArea.setText(myExporter.getReportText());
      myTextArea.getCaret().setDot(0);
    }

    protected JComponent createCenterPanel() {
      myTextArea = new JTextArea();
      myTextArea.setEditable(false);
      myTextScrollPane = new JScrollPane(myTextArea);
      myTextScrollPane.setPreferredSize(new Dimension(400, 300));
      return myTextScrollPane;
    }

    protected JComponent createNorthPanel() {
      JPanel filePanel = createFilePanel(myTfFile, myFileButton);
      JComponent settingsPanel = myExporter.getSettingsEditor();
      if (settingsPanel == null) {
        return filePanel;
      }
      JPanel northPanel = new JPanel(new BorderLayout());
      northPanel.add(filePanel, BorderLayout.NORTH);
      northPanel.add(settingsPanel, BorderLayout.CENTER);
      return northPanel;
    }

    protected JPanel createFilePanel(JTextField textField, JButton button) {
      JPanel panel = new JPanel();
      panel.setLayout(new GridBagLayout());
      GridBagConstraints gbConstraints = new GridBagConstraints();
      gbConstraints.fill = GridBagConstraints.HORIZONTAL;
      JLabel promptLabel = new JLabel(IdeBundle.message("editbox.export.to.file"));
      gbConstraints.weightx = 0;
      panel.add(promptLabel, gbConstraints);
      gbConstraints.weightx = 1;
      panel.add(textField, gbConstraints);
      gbConstraints.fill = 0;
      gbConstraints.weightx = 0;
      gbConstraints.insets = new Insets(0, 0, 0, 0);
      panel.add(button, gbConstraints);

      String defaultFilePath = myExporter.getDefaultFilePath();
      defaultFilePath = ((ProjectEx)myProject).getExpandMacroReplacements()
        .substitute(defaultFilePath, SystemInfo.isFileSystemCaseSensitive).replace('/', File.separatorChar);
      textField.setText(defaultFilePath);

      button.addActionListener(
          new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            browseFile();
          }
        }
      );

      return panel;
    }

    protected void browseFile() {
      JFileChooser chooser = new JFileChooser();
      if (myTfFile != null) {
        chooser.setCurrentDirectory(new File(myTfFile.getText()));
      }
      chooser.showOpenDialog(WindowManager.getInstance().suggestParentWindow(myProject));
      if (chooser.getSelectedFile() != null) {
        myTfFile.setText(chooser.getSelectedFile().getAbsolutePath());
      }
    }

    public String getText() {
      return myTextArea.getText();
    }

    public void setFileName(String s) {
      myTfFile.setText(s);
    }

    public String getFileName() {
      return myTfFile.getText();
    }

    protected Action[] createActions() {
      return new Action[]{getOKAction(), new CopyToClipboardAction(), getCancelAction()};
    }

    protected String getDimensionServiceKey() {
      return "#com.intellij.ide.util.ExportDialog";
    }

    protected class CopyToClipboardAction extends AbstractAction {
      public CopyToClipboardAction() {
        super(IdeBundle.message("button.copy"));
        putValue(AbstractAction.SHORT_DESCRIPTION, IdeBundle.message("description.copy.text.to.clipboard"));
      }

      public void actionPerformed(ActionEvent e) {
        String s = StringUtil.convertLineSeparators(myTextArea.getText(), "\n");
        CopyPasteManager.getInstance().setContents(new StringSelection(s));
      }
    }
  };
}

package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.OrderPanel;
import com.intellij.ui.OrderPanelListener;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 12.07.2005
 * Time: 18:59:52
 * To change this template use File | Settings | File Templates.
 */
public class SelectFilesDialog extends DialogWrapper implements ActionListener {

  private String[] myFiles;
  private FilesList myFilesList;
  private JButton mySelectAllButton;
  private JButton myDeselectAllButton;
  private String myLabel;

  public SelectFilesDialog(final Project project, String label, String title, String actionName, String[] paths) {
    super(project, true);
    setOKButtonText(actionName);
    setTitle(title);
    setResizable(true);

    myFiles = paths;
    myLabel = label;

    init();
  }

  protected void init() {
    super.init();

    mySelectAllButton.addActionListener(this);
    myDeselectAllButton.addActionListener(this);

    myFilesList.addListener(new OrderPanelListener() {
      public void entryMoved() {
        getOKAction().setEnabled(isOKActionEnabled());
      }
    });
  }

  protected String getDimensionServiceKey() {
    return "svn.selectFilesDialog";
  }

  public boolean shouldCloseOnCross() {
    return true;
  }

  public JComponent getPreferredFocusedComponent() {
    return myFilesList;
  }

  public boolean isOKActionEnabled() {
    return myFilesList.getSelectedPaths().length > 0;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout(5,5));

    JLabel label = new JLabel(myLabel);
    panel.add(label, BorderLayout.NORTH);

    myFilesList = new FilesList(myFiles);
    myFilesList.setCheckboxColumnName("");
    myFilesList.setEntriesEditable(false);
    for (int i = 0; i < myFiles.length; i++) {
      myFilesList.add(myFiles[i]);
    }
    Font font = myFilesList.getFont();
    FontMetrics fm = myFilesList.getFontMetrics(font);
    int height = fm.getHeight();
    myFilesList.setPreferredSize(new Dimension(myFilesList.getPreferredSize().width, height*7));
    panel.add(new JScrollPane(myFilesList), BorderLayout.CENTER);

    JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    mySelectAllButton = new JButton("Select &All");
    myDeselectAllButton = new JButton("&Deselect All");

    DialogUtil.registerMnemonic(mySelectAllButton);
    DialogUtil.registerMnemonic(myDeselectAllButton);

    buttonsPanel.add(mySelectAllButton);
    buttonsPanel.add(myDeselectAllButton);

    panel.add(buttonsPanel, BorderLayout.SOUTH);
    return panel;
  }

  public void actionPerformed(ActionEvent e) {
    for (int i = 0; i < myFiles.length; i++) {
      myFilesList.setChecked(myFiles[i], e.getSource() == mySelectAllButton);
    }
    myFilesList.refresh();
    setOKActionEnabled(isOKActionEnabled());
  }

  public String[] getSelectedPaths() {
    return myFilesList.getSelectedPaths();
  }

  private class FilesList extends OrderPanel {

    private Map mySelectedFiles;

    protected FilesList(String[] files) {
      super(String.class, true);
      mySelectedFiles = new TreeMap();
      for (int i = 0; i < files.length; i++) {
        mySelectedFiles.put(files[i], Boolean.TRUE);
      }
    }
    public boolean isCheckable(final Object entry) {
      return true;
    }
    public boolean isChecked(final Object entry) {
      return Boolean.TRUE.equals(mySelectedFiles.get(entry));
    }
    public void setChecked(final Object entry, final boolean checked) {
      mySelectedFiles.put(entry, checked ? Boolean.TRUE : Boolean.FALSE);
      getOKAction().setEnabled(isOKActionEnabled());
    }

    public void refresh() {
      clear();
      for (Iterator paths = mySelectedFiles.keySet().iterator(); paths.hasNext();) {
        String path = (String)paths.next();
        add(path);
      }
    }

    public String[] getSelectedPaths() {
      Collection selected = new TreeSet();
      for (Iterator paths = mySelectedFiles.keySet().iterator(); paths.hasNext();) {
        String path = (String)paths.next();
        if (isChecked(path)) {
          selected.add(path);
        }
      }
      return (String[])selected.toArray(new String[selected.size()]);
    }
  }
}

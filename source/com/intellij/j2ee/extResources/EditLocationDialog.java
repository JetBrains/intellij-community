package com.intellij.j2ee.extResources;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

public class EditLocationDialog extends DialogWrapper {

  private JTextField myTfUrl;
  private JTextField myTfPath;
  private FixedSizeButton myBtnBrowseLocalPath;
  private Project myProject;
  private boolean myShowPath;

  private String myTitle;
  private String myName;
  private String myLocation;

  public EditLocationDialog(Project project, boolean showPath) {
    super(project, true);
    myProject = project;
    myShowPath = showPath;
    myTitle = "External Resource";
    myName = "URI:";
    myLocation = "Path:";
    init();
  }

  public EditLocationDialog(Project project, boolean showPath, String title, String name, String location) {
    super(project, true);
    myProject = project;
    myShowPath = showPath;
    myTitle = title;
    myName = name;
    myLocation = location;
    init();
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    panel.add(
        new JLabel(myName),
        new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 5, 3, 5), 0, 0)
    );
    panel.add(
        myTfUrl,
        new GridBagConstraints(0, 1, 2, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0, 5, 5, 5), 0, 0)
    );

    myTfUrl.setPreferredSize(new Dimension(350, myTfUrl.getPreferredSize().height));

  if (myShowPath) {
      panel.add(
          new JLabel(myLocation),
          new GridBagConstraints(0, 2, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 5, 3, 5), 0, 0)
      );
      panel.add(
          myTfPath,
          new GridBagConstraints(0, 3, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0, 5, 10, 0), 0, 0)
      );
      panel.add(
          myBtnBrowseLocalPath,
          new GridBagConstraints(1, 3, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 10, 5), 0, 0)
      );

      //
    TextFieldWithBrowseButton.MyDoClickAction.addTo(myBtnBrowseLocalPath, myTfPath);
    myBtnBrowseLocalPath.addActionListener(
          new ActionListener() {
            public void actionPerformed(ActionEvent ignored) {
              FileChooserDescriptor descriptor = getChooserDescriptor();
              VirtualFile[] files = FileChooser.chooseFiles(myProject, descriptor);
              if (files.length != 0) {
                myTfPath.setText(files[0].getPath().replace('/', File.separatorChar));
              }
            }
          }
      );
    }

    //

    return panel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myTfUrl;
  }

  public Pair getPair() {
    String path = myTfPath.getText().trim();
    String url = myTfUrl.getText().trim();
    return new Pair(url, path);
  }

  protected FileChooserDescriptor getChooserDescriptor(){
    return new FileChooserDescriptor(true, true, false, false, false, false);
  }

  protected void init() {
    setTitle(myTitle);
    myTfUrl = new JTextField();
    myTfPath = new JTextField();
    myBtnBrowseLocalPath = new FixedSizeButton(myTfPath);
    super.init();
  }

  /**
   * Initializes editor with the passed data.
   */
  public void init(String name, String location) {
    myTfUrl.setText(name);
    myTfPath.setText(location);
  }

  public static class Pair implements Comparable {
    String myName;
    String myLocation;

    public Pair(String name, String location) {
      myName = name;
      myLocation = location;
    }

    public int compareTo(Object o) {
      return myName.compareTo(((Pair)o).myName);
    }

    public String getName() {
      return myName;
    }

    public String getLocation(){
      return myLocation;
    }
  }
}

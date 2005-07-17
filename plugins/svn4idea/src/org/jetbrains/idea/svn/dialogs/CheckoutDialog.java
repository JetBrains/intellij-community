package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.PathUtil;

import javax.swing.*;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 25.06.2005
 * Time: 17:12:47
 * To change this template use File | Settings | File Templates.
 */
public class CheckoutDialog extends DialogWrapper implements ActionListener {
  private JComboBox myRepositoryBox;
  private Project myProject;
  private TextFieldWithBrowseButton myDestinationText;
  private FileChooserDescriptor myBrowserDescriptor;
  private RepositoryBrowserComponent myRepositoryBrowser;
  private boolean myIsDialogClosed;
  private boolean myIsDstChanged;

  public CheckoutDialog(Project project) {
    super(project, true);
    myProject = project;
    setTitle("Check Out Files");
    init();
  }

  protected String getDimensionServiceKey() {
    return "svn.checkoutDialog";
  }

  protected void doOKAction() {
    myIsDialogClosed = true;
    saveLastURL();
    super.doOKAction();
  }

  public void doCancelAction() {
    myIsDialogClosed = true;
    saveLastURL();
    super.doCancelAction();
  }

  private void saveLastURL() {
    if (myRepositoryBrowser.getRootURL() == null) {
      return;
    }
    if (myProject != null) {
      SvnVcs vcs = SvnVcs.getInstance(myProject);
      if (vcs != null) {
        String url = myRepositoryBrowser.getRootURL();
        vcs.getSvnConfiguration().setLastSelectedCheckoutURL(url);
      }
    }
  }

  protected void init() {
    super.init();

    SvnConfiguration config = SvnConfiguration.getInstance(myProject);
    Collection urls = config == null ? Collections.EMPTY_LIST : config.getCheckoutURLs();

    VirtualFile file = myProject.getProjectFile();
    if (file != null && file.getParent() != null) {
      VirtualFile parent = file.getParent();
      if (parent != null) {
        String defaultPath = parent.getPath().replace('/', File.separatorChar);
        myDestinationText.setText(defaultPath);
      }
    }
    myDestinationText.getTextField().selectAll();
    myDestinationText.getTextField().getDocument().addDocumentListener(new DocumentListener() {
      public void insertUpdate(DocumentEvent e) {
        getOKAction().setEnabled(isOKActionEnabled());
      }

      public void removeUpdate(DocumentEvent e) {
        getOKAction().setEnabled(isOKActionEnabled());
      }

      public void changedUpdate(DocumentEvent e) {
        getOKAction().setEnabled(isOKActionEnabled());
      }
    });

    String lastURL = null;
    for (Iterator us = urls.iterator(); us.hasNext();) {
      String url = (String)us.next();
      myRepositoryBox.addItem(url);
      lastURL = url;
    }

    if (config != null && config.getLastSelectedCheckoutURL() != null) {
      lastURL = config.getLastSelectedCheckoutURL();
    }
    if (lastURL != null) {
      myRepositoryBox.getEditor().setItem(lastURL);
      myRepositoryBox.setSelectedItem(lastURL);
    }
    else {
      lastURL = (String)myRepositoryBox.getSelectedItem();
    }
    myRepositoryBox.getEditor().selectAll();
    if (urls.isEmpty()) {
      myRepositoryBrowser.setRepositoryURL(null, false);
    }
    else {
      myRepositoryBrowser.setRepositoryURL(lastURL, false);
      if (myRepositoryBrowser.getRootURL() != null) {
        myRepositoryBrowser.getPreferredFocusedComponent().requestFocus();
      }
    }

    final JTextField editor = (JTextField)myRepositoryBox.getEditor().getEditorComponent();
    editor.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          e.consume();
          String url = editor.getText();
          myRepositoryBrowser.setRepositoryURL(url, false);
          if (myRepositoryBrowser.getRootURL() != null) {
            myRepositoryBrowser.getPreferredFocusedComponent().requestFocus();
          }
        }
      }
    });

    myRepositoryBox.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED && !myIsDialogClosed) {
          String url = (String)myRepositoryBox.getEditor().getItem();
          myRepositoryBrowser.setRepositoryURL(url, false);
          if (myRepositoryBrowser.getRootURL() != null) {
            SvnConfiguration.getInstance(myProject).addCheckoutURL(myRepositoryBrowser.getRootURL());
            myRepositoryBrowser.getPreferredFocusedComponent().requestFocus();
          }
        }
        getOKAction().setEnabled(isOKActionEnabled());
      }
    });
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());

    GridBagConstraints gc = new GridBagConstraints();
    gc.insets = new Insets(2, 2, 2, 2);
    gc.gridwidth = 1;
    gc.gridheight = 1;
    gc.gridx = 0;
    gc.gridy = 0;
    gc.anchor = GridBagConstraints.WEST;
    gc.fill = GridBagConstraints.NONE;
    gc.weightx = 0;
    gc.weighty = 0;

    JLabel label = new JLabel("Repository &URL:");
    panel.add(label, gc);
    gc.gridx += 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 1;

    myRepositoryBox = new JComboBox();
    myRepositoryBox.setEditable(true);
    panel.add(myRepositoryBox, gc);
    DialogUtil.registerMnemonic(label, myRepositoryBox);

    gc.gridx += 1;
    gc.fill = GridBagConstraints.NONE;
    gc.anchor = GridBagConstraints.EAST;
    gc.weightx = 0;

    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new RefreshAction());
    ActionToolbar tb = ActionManager.getInstance().createActionToolbar("", group, false);
    panel.add(tb.getComponent(), gc);

    gc.gridy += 1;
    gc.gridx = 0;
    gc.weightx = 1;
    gc.weighty = 1;
    gc.fill = GridBagConstraints.BOTH;
    gc.anchor = GridBagConstraints.WEST;
    gc.gridwidth = 3;

    myRepositoryBrowser = new RepositoryBrowserComponent(SvnVcs.getInstance(myProject));
    panel.add(myRepositoryBrowser, gc);

    gc.gridy += 1;
    gc.weightx = 0;
    gc.weighty = 0;
    gc.gridwidth = 1;
    gc.fill = GridBagConstraints.NONE;

    panel.add(new JLabel("Checkout From:"), gc);
    gc.gridx += 1;
    gc.gridwidth = 2;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;

    final JLabel URLLabel = new JLabel();
    URLLabel.setFont(URLLabel.getFont().deriveFont(Font.BOLD));
    panel.add(URLLabel, gc);

    myRepositoryBrowser.addChangeListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent treeSelectionEvent) {
        String url = myRepositoryBrowser.getSelectedURL();
        if (url == null) {
          URLLabel.setText("");
        }
        else {
          URLLabel.setText(url);
          if (!myIsDstChanged) {
            String path = myDestinationText.getText();
            if (path != null && path.trim().length() > 0) {
              path = path.replace(File.separatorChar, '/');
              if (!PathUtil.isEmpty(path)) {
                path = PathUtil.removeTail(path);
                path = PathUtil.append(path, PathUtil.decode(PathUtil.tail(url)));
              }
              else {
                path = PathUtil.decode(PathUtil.tail(url));
              }
              path = path.replace('/', File.separatorChar);
            }
            myDestinationText.setText(path);
            myDestinationText.getTextField().selectAll();

          }
        }
        getOKAction().setEnabled(isOKActionEnabled());
      }
    });

    gc.gridy += 1;
    gc.weightx = 0;
    gc.weighty = 0;
    gc.gridx = 0;
    gc.gridwidth = 1;
    gc.fill = GridBagConstraints.NONE;

    JLabel checkoutFileLabel = new JLabel("Checkout &To:");
    panel.add(checkoutFileLabel, gc);
    gc.gridx += 1;
    gc.gridwidth = 2;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;

    myDestinationText = new TextFieldWithBrowseButton(this);
    myDestinationText.setEditable(true);
    panel.add(myDestinationText, gc);

    gc.gridy += 1;
    gc.gridx = 0;
    gc.gridwidth = 3;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.anchor = GridBagConstraints.WEST;

    DialogUtil.registerMnemonic(checkoutFileLabel, myDestinationText);
    panel.add(new JSeparator(), gc);

    return panel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myRepositoryBox;
  }

  public boolean shouldCloseOnCross() {
    return true;
  }

  public boolean isOKActionEnabled() {
    return getSelectedURL() != null &&
           myDestinationText != null && myDestinationText.getText().trim().length() > 0;
  }

  public String getSelectedURL() {
    return myRepositoryBrowser.getSelectedURL();
  }

  public void actionPerformed(ActionEvent e) {
    if (myBrowserDescriptor == null) {
      myBrowserDescriptor = new FileChooserDescriptor(false, true, false, false, false, true);
      myBrowserDescriptor.setShowFileSystemRoots(true);
      myBrowserDescriptor.setTitle("Select Checkout Destination");
      myBrowserDescriptor.setDescription("Select directory to checkout remote directory into or create new one");
      myBrowserDescriptor.setHideIgnored(false);
    }
    String path = myDestinationText.getText().trim();
    path = "file://" + path.replace(File.separatorChar, '/');
    VirtualFile root = VirtualFileManager.getInstance().findFileByUrl(path);
    VirtualFile[] files = FileChooser.chooseFiles(myDestinationText, myBrowserDescriptor, root);
    if (files == null || files.length != 1 || files[0] == null) {
      return;
    }
    myIsDstChanged = true;
    myDestinationText.setText(files[0].getPath().replace('/', File.separatorChar));
    myDestinationText.getTextField().selectAll();
    getOKAction().setEnabled(isOKActionEnabled());
  }

  public String getSelectedFile() {
    return myDestinationText.getText();
  }

  private class RefreshAction extends AnAction {
    public void update(AnActionEvent anActionEvent) {
      anActionEvent.getPresentation().setText("Refresh");
      anActionEvent.getPresentation().setIcon(IconLoader.findIcon("/actions/sync.png"));
      String url = (String)myRepositoryBox.getEditor().getItem();
      try {
        SVNRepositoryLocation.parseURL(url);
      }
      catch (SVNException e) {
        url = null;
      }
      anActionEvent.getPresentation().setEnabled(url != null);
    }

    public void actionPerformed(AnActionEvent anActionEvent) {
      String url = (String)myRepositoryBox.getEditor().getItem();
      myRepositoryBrowser.setRepositoryURL(url, false);
      if (myRepositoryBrowser.getRootURL() != null) {
        myRepositoryBrowser.getPreferredFocusedComponent().requestFocus();
      }
    }
  }

}

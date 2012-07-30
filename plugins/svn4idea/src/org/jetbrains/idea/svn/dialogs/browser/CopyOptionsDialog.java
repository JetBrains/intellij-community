/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.dialogs.browser;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.PopupHandler;
import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.RepositoryBrowserComponent;
import org.jetbrains.idea.svn.dialogs.RepositoryBrowserDialog;
import org.jetbrains.idea.svn.dialogs.RepositoryTreeNode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;

public class CopyOptionsDialog extends DialogWrapper {

  private final SVNURL myURL;
  private EditorTextField myCommitMessage;
  private final Project myProject;
  private JTextField myNameField;
  private JLabel myURLLabel;
  private RepositoryBrowserComponent myBrowser;
  private JLabel myTargetURL;
  private JComboBox myMessagesBox;
  private JPanel myMainPanel;

  public CopyOptionsDialog(String title, Project project, final RepositoryTreeNode root, final RepositoryTreeNode node,
                           final boolean copy) {
    super(project, true);
    myProject = project;
    myURL = node.getURL();
    createUI();

    if (copy) {
      myTargetURL.setForeground(FileStatus.COLOR_ADDED);
      setOKButtonText("Copy");
    } else {
      myTargetURL.setForeground(FileStatus.COLOR_MODIFIED);
      setOKButtonText("Move");
    }

    myURLLabel.setText(myURL.toString());

    final TreeNode[] path = node.getSelfPath();
    final TreeNode[] subPath = new TreeNode[path.length - 1];
    System.arraycopy(path, 1, subPath, 0, path.length - 1);

    myBrowser.setRepositoryURL(root.getURL(), false, 
        new OpeningExpander.Factory(subPath, (RepositoryTreeNode)((node.getParent() instanceof RepositoryTreeNode) ? node.getParent() : null)));
    myBrowser.addChangeListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        update();
      }
    });

    myNameField.setText(SVNPathUtil.tail(myURL.getPath()));
    myNameField.selectAll();
    myNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        update();
      }
    });

    ArrayList<String> messages = VcsConfiguration.getInstance(myProject).getRecentMessages();
    Collections.reverse(messages);
    Object[] model = messages.toArray();
    myMessagesBox.setModel(new DefaultComboBoxModel(model));
    myMessagesBox.setRenderer(new MessageBoxCellRenderer());

    String lastMessage = VcsConfiguration.getInstance(myProject).getLastNonEmptyCommitMessage();
    if (lastMessage != null) {
      myCommitMessage.setText(lastMessage);
      myCommitMessage.selectAll();
    }
    myMessagesBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final Object item = myMessagesBox.getSelectedItem();
        if (item != null) {
          myCommitMessage.setText(item.toString());
          myCommitMessage.selectAll();
        }
      }
    });
    Disposer.register(getDisposable(), myBrowser);

    setTitle(title);
    init();
    update();
  }

  private void createUI() {
    myMainPanel = new JPanel(new BorderLayout());
    myBrowser = new RepositoryBrowserComponent(SvnVcs.getInstance(myProject));

    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new RepositoryBrowserDialog.MkDirAction(myBrowser) {
      @Override
      public void update(AnActionEvent e) {
        super.update(e);
        e.getPresentation().setText("New Remote Folder...");
      }
    });
    group.add(new RepositoryBrowserDialog.DeleteAction(myBrowser));
    group.add(new RepositoryBrowserDialog.RefreshAction(myBrowser));
    final ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu("", group);
    final JPopupMenu component = popupMenu.getComponent();
    myBrowser.getRepositoryTree().addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        component.show(comp, x, y);
      }
    });

    final Splitter splitter = new Splitter(true);
    splitter.setProportion(0.7f);
    final JPanel wrapper = createBrowserPartWrapper();
    splitter.setFirstComponent(wrapper);
    final JPanel commitMessageWrapper = createCommitMessageWrapper();
    splitter.setSecondComponent(commitMessageWrapper);

    myMainPanel.add(splitter, BorderLayout.CENTER);
    final JPanel recentMessagesWrapper = new JPanel(new BorderLayout());
    recentMessagesWrapper.add(new JLabel("Recent Messages:"), BorderLayout.NORTH);
    myMessagesBox = new JComboBox();
    recentMessagesWrapper.add(myMessagesBox, BorderLayout.SOUTH);
    recentMessagesWrapper.setBorder(BorderFactory.createEmptyBorder(4,0,0,0));
    myMainPanel.add(recentMessagesWrapper, BorderLayout.SOUTH);
  }

  private JPanel createCommitMessageWrapper() {
    final JPanel commitMessageWrapper = new JPanel(new BorderLayout());
    commitMessageWrapper.add(new JLabel("Commit Message:"), BorderLayout.NORTH);

    myCommitMessage = CommitMessage.createCommitTextEditor(myProject, true);

    commitMessageWrapper.add(myCommitMessage, BorderLayout.CENTER);
    return commitMessageWrapper;
  }

  private JPanel createBrowserPartWrapper() {
    final JPanel wrapper = new JPanel(new GridBagLayout());
    final GridBag gridBag = new GridBag().setDefaultAnchor(GridBagConstraints.NORTHWEST)
      .setDefaultFill(GridBagConstraints.NONE).setDefaultInsets(1,1,1,1).setDefaultWeightX(1).setDefaultWeightY(0);

    gridBag.nextLine().next();
    gridBag.weightx(0);
    wrapper.add(new JLabel("Source URL:"), gridBag);

    gridBag.next();
    gridBag.fillCellHorizontally();
    myURLLabel = new JLabel();
    myURLLabel.setFont(myURLLabel.getFont().deriveFont(Font.BOLD));
    wrapper.add(myURLLabel, gridBag);

    gridBag.nextLine().next();
    gridBag.weightx(0);
    gridBag.pady(4);
    wrapper.add(new JLabel("Target Location:"), gridBag);

    gridBag.nextLine().next();
    gridBag.fillCell();
    gridBag.weighty(1);
    gridBag.coverLine(2);
    wrapper.add(myBrowser, gridBag);

    gridBag.nextLine().next();
    gridBag.weightx(0);
    gridBag.pady(4);
    wrapper.add(new JLabel("Target Name:"), gridBag);

    gridBag.next();
    gridBag.fillCellHorizontally();
    myNameField = new JTextField();
    wrapper.add(myNameField, gridBag);

    gridBag.nextLine().next();
    gridBag.weightx(0);
    gridBag.pady(2);
    wrapper.add(new JLabel("Target URL:"), gridBag);

    gridBag.next();
    gridBag.fillCellHorizontally();
    myTargetURL = new JLabel();
    myTargetURL.setFont(myTargetURL.getFont().deriveFont(Font.BOLD));
    wrapper.add(myTargetURL, gridBag);
    return wrapper;
  }

  @NonNls
  protected String getDimensionServiceKey() {
    return "svn4idea.copy.options";
  }

  public String getCommitMessage() {
    return myCommitMessage.getText();
  }

  public SVNURL getSourceURL() {
    return myURL;
  }

  public String getName() {
    return myNameField.getText();
  }

  @Nullable
  public SVNURL getTargetURL() {
    if (getOKAction().isEnabled()) {
      try {
        return SVNURL.parseURIEncoded(myTargetURL.getText());
      } catch (SVNException e) {
        //
      }
    }
    return null;
  }

  @Nullable
  public RepositoryTreeNode getTargetParentNode() {
    return myBrowser.getSelectedNode();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  private void update() {
    RepositoryTreeNode baseNode = myBrowser.getSelectedNode();
    if (baseNode == null) {
      myTargetURL.setText("");
      getOKAction().setEnabled(false);
      return;
    }
    SVNURL baseURL = baseNode.getURL();
    String name = myNameField.getText();
    if (name == null || "".equals(name)) {
      getOKAction().setEnabled(false);
      return;
    }
    try {
      baseURL = baseURL.appendPath(myNameField.getText(), false);
    } catch (SVNException e) {
      //
      getOKAction().setEnabled(false);
      return;
    }
    myTargetURL.setText(baseURL.toString());
    getOKAction().setEnabled(!myURL.toString().equals(myTargetURL.getText()));
  }


  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }
}

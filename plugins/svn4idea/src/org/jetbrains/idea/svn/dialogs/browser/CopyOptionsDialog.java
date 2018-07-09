// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs.browser;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.PopupHandler;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.dialogs.RepositoryBrowserComponent;
import org.jetbrains.idea.svn.dialogs.RepositoryBrowserDialog;
import org.jetbrains.idea.svn.dialogs.RepositoryTreeNode;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.util.ui.JBUI.Borders.emptyTop;
import static com.intellij.util.ui.JBUI.Panels.simplePanel;
import static com.intellij.util.ui.JBUI.insets;
import static org.jetbrains.idea.svn.SvnUtil.append;
import static org.jetbrains.idea.svn.SvnUtil.createUrl;

public class CopyOptionsDialog extends DialogWrapper {

  private final Url myURL;
  private CommitMessage myCommitMessage;
  private final Project myProject;
  private JTextField myNameField;
  private JLabel myURLLabel;
  private RepositoryBrowserComponent myBrowser;
  private JLabel myTargetURL;
  private BorderLayoutPanel myMainPanel;

  public CopyOptionsDialog(String title, Project project, RepositoryTreeNode root, RepositoryTreeNode node, boolean copy) {
    super(project, true);
    myProject = project;
    myURL = node.getURL();
    createUI();

    myTargetURL.setForeground(copy ? FileStatus.ADDED.getColor() : FileStatus.MODIFIED.getColor());
    setOKButtonText(copy ? "Copy" : "Move");
    myURLLabel.setText(myURL.toDecodedString());

    TreeNode[] path = node.getSelfPath();
    TreeNode[] subPath = new TreeNode[path.length - 1];
    System.arraycopy(path, 1, subPath, 0, path.length - 1);

    myBrowser.setRepositoryURL(root.getURL(), false, new OpeningExpander.Factory(
      subPath, node.getParent() instanceof RepositoryTreeNode ? (RepositoryTreeNode)node.getParent() : null));
    myBrowser.addChangeListener(e -> update());

    myNameField.setText(myURL.getTail());
    myNameField.selectAll();
    myNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        update();
      }
    });

    String lastMessage = VcsConfiguration.getInstance(myProject).getLastNonEmptyCommitMessage();
    if (lastMessage != null) {
      myCommitMessage.setText(lastMessage);
      myCommitMessage.getEditorField().selectAll();
    }
    Disposer.register(getDisposable(), myBrowser);

    setTitle(title);
    init();
    update();
  }

  @NotNull
  public static ComboBox<String> configureRecentMessagesComponent(@NotNull Project project,
                                                                  @NotNull ComboBox<String> comboBox,
                                                                  @NotNull Consumer<String> messageConsumer) {
    List<String> messages = VcsConfiguration.getInstance(project).getRecentMessages();
    Collections.reverse(messages);
    CollectionComboBoxModel<String> model = new CollectionComboBoxModel<>(messages);

    comboBox.setModel(model);
    comboBox.setRenderer(new MessageBoxCellRenderer());
    comboBox.addActionListener(e -> messageConsumer.accept(model.getSelected()));

    return comboBox;
  }

  private void createUI() {
    myMainPanel = simplePanel();
    myBrowser = new RepositoryBrowserComponent(SvnVcs.getInstance(myProject));

    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new RepositoryBrowserDialog.MkDirAction(myBrowser) {
      @Override
      public void update(AnActionEvent e) {
        super.update(e);
        e.getPresentation().setText("New Remote Folder...");
      }
    });
    group.add(new RepositoryBrowserDialog.DeleteAction(myBrowser));
    group.add(new RepositoryBrowserDialog.RefreshAction(myBrowser));
    ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu("", group);
    JPopupMenu component = popupMenu.getComponent();
    myBrowser.getRepositoryTree().addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        component.show(comp, x, y);
      }
    });

    Splitter splitter = new Splitter(true, 0.7f);
    splitter.setFirstComponent(createBrowserPartWrapper());
    splitter.setSecondComponent(createCommitMessageWrapper());

    myMainPanel.addToCenter(splitter);
    ComboBox<String> messagesBox = configureRecentMessagesComponent(myProject, new ComboBox<>(), message -> {
      if (message != null) {
        myCommitMessage.setText(message);
        myCommitMessage.getEditorField().selectAll();
      }
    });
    myMainPanel.addToBottom(simplePanel().addToTop(new JLabel("Recent Messages:")).addToBottom(messagesBox).withBorder(emptyTop(4)));
  }

  @NotNull
  private JPanel createCommitMessageWrapper() {
    myCommitMessage = new CommitMessage(myProject, false, false, true);

    return simplePanel(myCommitMessage).addToTop(new JLabel("Commit Message:"));
  }

  @NotNull
  private JPanel createBrowserPartWrapper() {
    JPanel wrapper = new JPanel(new GridBagLayout());
    GridBag gridBag =
      new GridBag().setDefaultAnchor(GridBagConstraints.NORTHWEST).setDefaultFill(GridBagConstraints.NONE).setDefaultInsets(insets(1))
        .setDefaultWeightX(1).setDefaultWeightY(0);

    gridBag.nextLine().next().weightx(0);
    wrapper.add(new JLabel("Source URL:"), gridBag);

    gridBag.next().fillCellHorizontally();
    myURLLabel = new JLabel();
    myURLLabel.setFont(myURLLabel.getFont().deriveFont(Font.BOLD));
    wrapper.add(myURLLabel, gridBag);

    gridBag.nextLine().next().weightx(0).pady(4);
    wrapper.add(new JLabel("Target Location:"), gridBag);

    gridBag.nextLine().next().fillCell().weighty(1).coverLine(2);
    wrapper.add(myBrowser, gridBag);

    gridBag.nextLine().next().weightx(0).pady(4);
    wrapper.add(new JLabel("Target Name:"), gridBag);

    gridBag.next().fillCellHorizontally();
    myNameField = new JTextField();
    wrapper.add(myNameField, gridBag);

    gridBag.nextLine().next().weightx(0).pady(2);
    wrapper.add(new JLabel("Target URL:"), gridBag);

    gridBag.next().fillCellHorizontally();
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
    return myCommitMessage.getComment();
  }

  public Url getSourceURL() {
    return myURL;
  }

  public String getName() {
    return myNameField.getText();
  }

  @Nullable
  public Url getTargetURL() {
    if (getOKAction().isEnabled()) {
      try {
        return createUrl(myTargetURL.getText());
      }
      catch (SvnBindException ignored) {
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
    Url baseURL = baseNode.getURL();
    String name = myNameField.getText();
    if (isEmpty(name)) {
      getOKAction().setEnabled(false);
      return;
    }
    try {
      baseURL = append(baseURL, myNameField.getText());
    }
    catch (SvnBindException e) {
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

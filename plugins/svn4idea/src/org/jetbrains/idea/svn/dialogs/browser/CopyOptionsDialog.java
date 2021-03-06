// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBLabel;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.util.ui.JBUI.Borders.emptyTop;
import static com.intellij.util.ui.JBUI.Panels.simplePanel;
import static com.intellij.util.ui.JBUI.insets;
import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.SvnBundle.messagePointer;
import static org.jetbrains.idea.svn.SvnUtil.append;

public class CopyOptionsDialog extends DialogWrapper {

  private final Url myURL;
  private Url myTargetUrl;

  private CommitMessage myCommitMessage;
  private final Project myProject;
  private JTextField myNameField;
  private JBLabel myURLLabel;
  private RepositoryBrowserComponent myBrowser;
  private JBLabel myTargetURLLabel;
  private BorderLayoutPanel myMainPanel;

  public CopyOptionsDialog(Project project, RepositoryTreeNode root, RepositoryTreeNode node, boolean copy) {
    super(project, true);
    myProject = project;
    myURL = node.getURL();
    createUI();

    myTargetURLLabel.setForeground(copy ? FileStatus.ADDED.getColor() : FileStatus.MODIFIED.getColor());
    setOKButtonText(copy ? message("button.copy") : message("button.move"));
    myURLLabel.setText(myURL.toDecodedString());

    TreeNode[] path = node.getSelfPath();
    TreeNode[] subPath = Arrays.copyOfRange(path, 1, path.length);

    myBrowser.setRepositoryURL(root.getURL(), false, new OpeningExpander.Factory(
      subPath, node.getParent() instanceof RepositoryTreeNode ? (RepositoryTreeNode)node.getParent() : null));
    myBrowser.addChangeListener(e -> update());

    myNameField.setText(myURL.getTail());
    myNameField.selectAll();
    myNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        update();
      }
    });

    String lastMessage = VcsConfiguration.getInstance(myProject).getLastNonEmptyCommitMessage();
    if (lastMessage != null) {
      myCommitMessage.setText(lastMessage);
      myCommitMessage.getEditorField().selectAll();
    }
    Disposer.register(getDisposable(), myBrowser);

    setTitle(copy ? message("copy.dialog.title") : message("move.dialog.title"));
    init();
    update();
  }

  @NotNull
  public static ComboBox<String> configureRecentMessagesComponent(@NotNull Project project,
                                                                  @NotNull ComboBox<String> comboBox,
                                                                  @NotNull Consumer<? super String> messageConsumer) {
    List<String> messages = VcsConfiguration.getInstance(project).getRecentMessages();
    Collections.reverse(messages);
    CollectionComboBoxModel<String> model = new CollectionComboBoxModel<>(messages);

    comboBox.setModel(model);
    comboBox.setRenderer(SimpleListCellRenderer.create("", commitMessage -> getPresentableCommitMessage(commitMessage)));
    comboBox.addActionListener(e -> messageConsumer.accept(model.getSelected()));

    return comboBox;
  }

  @NlsSafe
  private static String getPresentableCommitMessage(@NotNull String commitMessage) {
    return commitMessage.replace('\r', '|').replace('\n', '|');
  }

  private void createUI() {
    myMainPanel = simplePanel();
    myBrowser = new RepositoryBrowserComponent(SvnVcs.getInstance(myProject));

    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new RepositoryBrowserDialog.MkDirAction(myBrowser) {
      @Override
      public void update(@NotNull AnActionEvent e) {
        super.update(e);
        e.getPresentation().setText(messagePointer("action.new.remote.folder.text"));
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
    myMainPanel.addToBottom(
      simplePanel()
        .addToTop(new JBLabel(message("label.recent.messages")))
        .addToBottom(messagesBox)
        .withBorder(emptyTop(4))
    );
  }

  @NotNull
  private JPanel createCommitMessageWrapper() {
    myCommitMessage = new CommitMessage(myProject, false, false, true);
    Disposer.register(getDisposable(), myCommitMessage);

    return simplePanel(myCommitMessage).addToTop(new JBLabel(message("label.commit.message")));
  }

  @NotNull
  private JPanel createBrowserPartWrapper() {
    JPanel wrapper = new JPanel(new GridBagLayout());
    GridBag gridBag =
      new GridBag().setDefaultAnchor(GridBagConstraints.NORTHWEST).setDefaultFill(GridBagConstraints.NONE).setDefaultInsets(insets(1))
        .setDefaultWeightX(1).setDefaultWeightY(0);

    gridBag.nextLine().next().weightx(0);
    wrapper.add(new JBLabel(message("label.source.url")), gridBag);

    gridBag.next().fillCellHorizontally();
    myURLLabel = new JBLabel();
    myURLLabel.setFont(myURLLabel.getFont().deriveFont(Font.BOLD));
    wrapper.add(myURLLabel, gridBag);

    gridBag.nextLine().next().weightx(0).pady(4);
    wrapper.add(new JBLabel(message("label.target.location")), gridBag);

    gridBag.nextLine().next().fillCell().weighty(1).coverLine(2);
    wrapper.add(myBrowser, gridBag);

    gridBag.nextLine().next().weightx(0).pady(4);
    wrapper.add(new JBLabel(message("label.target.name")), gridBag);

    gridBag.next().fillCellHorizontally();
    myNameField = new JTextField();
    wrapper.add(myNameField, gridBag);

    gridBag.nextLine().next().weightx(0).pady(2);
    wrapper.add(new JBLabel(message("label.target.url")), gridBag);

    gridBag.next().fillCellHorizontally();
    myTargetURLLabel = new JBLabel();
    myTargetURLLabel.setFont(myTargetURLLabel.getFont().deriveFont(Font.BOLD));
    wrapper.add(myTargetURLLabel, gridBag);
    return wrapper;
  }

  @Override
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
    return myTargetUrl;
  }

  @Nullable
  public RepositoryTreeNode getTargetParentNode() {
    return myBrowser.getSelectedNode();
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  private void update() {
    myTargetUrl = buildTargetUrl();

    myTargetURLLabel.setText(myTargetUrl != null ? myTargetUrl.toDecodedString() : "");
    getOKAction().setEnabled(myTargetUrl != null && !myURL.equals(myTargetUrl));
  }

  private @Nullable Url buildTargetUrl() {
    RepositoryTreeNode locationNode = myBrowser.getSelectedNode();
    if (locationNode == null) return null;

    Url location = locationNode.getURL();
    String name = myNameField.getText();
    if (isEmpty(name)) return null;

    try {
      return append(location, name);
    }
    catch (SvnBindException e) {
      return null;
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }
}

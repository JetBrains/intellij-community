// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.config;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;

import static org.jetbrains.idea.svn.SvnBundle.message;

public class SvnConfigureProxiesComponent extends MasterDetailsComponent {
  private final ServersFileManager myManager;

  private final Runnable myTreeUpdaterValidator;
  private final Runnable myValidator;
  private JComponent myComponent;
  private final TestConnectionPerformer myTestConnectionPerformer;
  private ConfigureProxiesOptionsPanel myDefaultGroupPanel;

  public SvnConfigureProxiesComponent(final ServersFileManager manager, final GroupsValidator validator, final TestConnectionPerformer testConnectionPerformer) {
    myTestConnectionPerformer = testConnectionPerformer;
    myValidator = validator;
    myTreeUpdaterValidator = new CompositeRunnable(TREE_UPDATER, myValidator);
    initTree();
    myManager = manager;
    fillTree();
    validator.add(this);
  }

  @Override
  @NotNull
  public JComponent createComponent() {
    if (myComponent == null) {
      myComponent = super.createComponent();
    }
    return myComponent;
  }

  @Override
  public String getDisplayName() {
    return message("configurable.SvnConfigureProxiesComponent.display.name");
  }

  @Override
  public String getHelpTopic() {
    return null;
  }

  private void addGroup(final ProxyGroup template) {
    final ProxyGroup group;
    if (template == null) {
      group = new ProxyGroup(message("value.new.server.group.name"), "", new HashMap<>());
    } else {
      group = new ProxyGroup(message("value.new.server.group.name"), template.getPatterns(), template.getProperties());
    }

    addNode(createNodeForObject(group), myRoot);
    selectNodeInTree(group);
  }

  public List<String> getGlobalGroupRepositories(final Collection<String> all) {
    // i.e. all-[all used]
    final List<String> result = new LinkedList<>(all);

    for (int i = 0; i < myRoot.getChildCount(); i++) {
      final MyNode node = (MyNode) myRoot.getChildAt(i);
      final GroupConfigurable groupConfigurable = (GroupConfigurable) node.getConfigurable();
      if (! groupConfigurable.getEditableObject().isDefault()) {
        result.removeAll(groupConfigurable.getRepositories());
      }
    }

    return result;
  }

  public boolean validate(final ValidationListener listener) {
    final Set<String> checkSet = new HashSet<>();
    final AmbiguousPatternsFinder ambiguousPatternsFinder = new AmbiguousPatternsFinder();

    for (int i = 0; i < myRoot.getChildCount(); i++) {
      final MyNode node = (MyNode) myRoot.getChildAt(i);
      final GroupConfigurable groupConfigurable = (GroupConfigurable) node.getConfigurable();
      final String groupName = groupConfigurable.getEditableObject().getName();

      if (checkSet.contains(groupName)) {
        listener.onError(message("dialog.edit.http.proxies.settings.error.same.group.names.text", groupName), myComponent, true);
        return false;
      }
      checkSet.add(groupName);
    }

    for (int i = 0; i < myRoot.getChildCount(); i++) {
      final MyNode node = (MyNode)myRoot.getChildAt(i);
      final GroupConfigurable groupConfigurable = (GroupConfigurable)node.getConfigurable();
      groupConfigurable.applyImpl();

      String error = groupConfigurable.validate();
      if (error != null) {
        listener.onError(error, myComponent, false);
        return false;
      }

      if (!groupConfigurable.getEditableObject().isDefault()) {
        final String groupName = groupConfigurable.getEditableObject().getName();
        final List<String> urls = groupConfigurable.getRepositories();
        ambiguousPatternsFinder.acceptUrls(groupName, urls);
      }
    }

    String error = ambiguousPatternsFinder.validate();
    if (error != null) {
      listener.onError(error, myComponent, false);
      return false;
    }

    return true;
  }

  @Override
  protected ArrayList<AnAction> createActions(final boolean fromPopup) {
    ArrayList<AnAction> result = new ArrayList<>();
    result.add(new DumbAwareAction(SvnBundle.messagePointer("action.DumbAware.SvnConfigureProxiesComponent.text.add"),
                                   SvnBundle.messagePointer("action.DumbAware.SvnConfigureProxiesComponent.description.add"),
                                   IconUtil.getAddIcon()) {
        {
            registerCustomShortcutSet(CommonShortcuts.INSERT, myTree);
        }
        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
          addGroup(null);
        }


    });
    result.add(new MyDeleteAction(forAll(o -> {
      if (o instanceof MyNode node) {
        if (node.getConfigurable() instanceof GroupConfigurable) {
          final ProxyGroup group = ((GroupConfigurable)node.getConfigurable()).getEditableObject();
          return !group.isDefault();
        }
      }
      return false;
    })) {
      @Override
      public void actionPerformed(@NotNull final AnActionEvent e) {
        final TreePath path = myTree.getSelectionPath();
        final MyNode node = (MyNode)path.getLastPathComponent();
        final MyNode parentNode = (MyNode) node.getParent();
        int idx = parentNode.getIndex(node);

        super.actionPerformed(e);

        idx = (idx == parentNode.getChildCount()) ? idx - 1 : idx;
        if (parentNode.getChildCount() > 0) {
          final TreePath newSelectedPath = new TreePath(parentNode.getPath()).pathByAddingChild(parentNode.getChildAt(idx));
          myTree.setSelectionPath(newSelectedPath);
        }
      }
    });

    result.add(new DumbAwareAction(SvnBundle.messagePointer("action.DumbAware.SvnConfigureProxiesComponent.text.copy"),
                                   SvnBundle.messagePointer("action.DumbAware.SvnConfigureProxiesComponent.description.copy"),
                                   PlatformIcons.COPY_ICON) {
      {
        registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_MASK)), myTree);
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent event) {
        // apply - for update of editable object
        try {
          getSelectedConfigurable().apply();
        }
        catch (ConfigurationException e) {
          // suppress & wait for OK
        }
        final ProxyGroup selectedGroup = (ProxyGroup)getSelectedObject();
        if (selectedGroup != null) {
          addGroup(selectedGroup);
        }
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabled(getSelectedObject() != null);
      }
    });
    return result;
  }

  @Override
  public void apply() throws ConfigurationException {
    final List<ProxyGroup> groups = new ArrayList<>(myRoot.getChildCount());

    for (int i = 0; i < myRoot.getChildCount(); i++) {
      final MyNode node = (MyNode) myRoot.getChildAt(i);
      final GroupConfigurable groupConfigurable = (GroupConfigurable) node.getConfigurable();
      groupConfigurable.apply();
      groups.add(groupConfigurable.getEditableObject());
    }

    myManager.updateUserServerFile(groups);
  }

  @Override
  public void reset() {
    super.reset();
    myManager.updateFromFile();

    for (int i = 0; i < myRoot.getChildCount(); i++) {
      final MyNode node = (MyNode) myRoot.getChildAt(i);
      final GroupConfigurable groupConfigurable = (GroupConfigurable) node.getConfigurable();
      groupConfigurable.reset();
    }
  }

  private MyNode createNodeForObject(final ProxyGroup group) {
    final ConfigureProxiesOptionsPanel panel = new ConfigureProxiesOptionsPanel(myValidator, myTestConnectionPerformer);
    final GroupConfigurable gc = new GroupConfigurable(group, myTreeUpdaterValidator, panel);
    if (group.isDefault()) {
      myDefaultGroupPanel = panel;
    }

    // first added node must be global /default group. since url-fillers for other groups forwards their updates to global group
    assert myDefaultGroupPanel != null;
    panel.setPatternsListener((group.isDefault()) ? PatternsListener.Empty.instance :
                              new RepositoryUrlFilter(panel, this, myDefaultGroupPanel));

    return new MyNode(gc, group.isDefault());
  }

  private void fillTree() {
    myRoot.removeAllChildren();

    DefaultProxyGroup defaultProxyGroup = myManager.getDefaultGroup();
    defaultProxyGroup = (defaultProxyGroup == null) ? new DefaultProxyGroup(Collections.emptyMap()) : defaultProxyGroup;
    final Map<String, ProxyGroup> userGroups = myManager.getGroups();

    myRoot.add(createNodeForObject(defaultProxyGroup));
    for (Map.Entry<String, ProxyGroup> entry : userGroups.entrySet()) {
      myRoot.add(createNodeForObject(entry.getValue()));
    }

    TreeUtil.sortRecursively(myRoot, GroupNodesComparator.getInstance());
    ((DefaultTreeModel) myTree.getModel()).reload(myRoot);
  }

  @Override
  protected Comparator<MyNode> getNodeComparator() {
    return GroupNodesComparator.getInstance();
  }

  private static class GroupNodesComparator implements Comparator<MyNode> {
    private final static GroupNodesComparator instance = new GroupNodesComparator();

    private static GroupNodesComparator getInstance() {
      return instance;
    }

    @Override
    public int compare(final MyNode node1, final MyNode node2) {
      if ((node1.getConfigurable() instanceof GroupConfigurable) && (node2.getConfigurable() instanceof GroupConfigurable)) {
        final ProxyGroup group1 = ((GroupConfigurable) node1.getConfigurable()).getEditableObject();
        final ProxyGroup group2 = ((GroupConfigurable) node2.getConfigurable()).getEditableObject();
        if (group1.isDefault()) {
          return -1;
        }
        if (group2.isDefault()) {
          return 1;
        }
      }

      return node1.getDisplayName().compareToIgnoreCase(node2.getDisplayName());
    }
  }

  /**
   * total component made invalid => for test connection to be forbidden when for ex. ambiguous settings given
   */
  public void setIsValid(final boolean valid) {
    for (int i = 0; i < myRoot.getChildCount(); i++) {
      final MyNode node = (MyNode) myRoot.getChildAt(i);
      final GroupConfigurable groupConfigurable = (GroupConfigurable) node.getConfigurable();
      groupConfigurable.setIsValid(valid);
    }
  }

  private static class CompositeRunnable implements Runnable {
    private final Runnable[] myRunnables;

    CompositeRunnable(Runnable @NotNull ... runnables) {
      myRunnables = runnables;
    }

    @Override
    public void run() {
      for (Runnable runnable : myRunnables) {
        runnable.run();
      }
    }
  }
}

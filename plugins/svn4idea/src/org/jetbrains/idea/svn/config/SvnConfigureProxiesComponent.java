/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.idea.svn.config;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.util.Ref;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnServerFileManager;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;

public class SvnConfigureProxiesComponent extends MasterDetailsComponent {
  private final SvnServerFileManager myManager;

  private final CompositeRunnable myTreeUpdaterValidator;
  private final Runnable myValidator;
  private JComponent myComponent;
  private final TestConnectionPerformer myTestConnectionPerformer;
  private ConfigureProxiesOptionsPanel myDefaultGroupPanel;

  public SvnConfigureProxiesComponent(final SvnServerFileManager manager, final GroupsValidator validator, final TestConnectionPerformer testConnectionPerformer) {
    myTestConnectionPerformer = testConnectionPerformer;
    myValidator = validator;
    myTreeUpdaterValidator = new CompositeRunnable(TREE_UPDATER, myValidator);
    initTree();
    myManager = manager;
    fillTree();
    validator.add(this);
  }

  @NotNull
  public JComponent createComponent() {
    if (myComponent == null) {
      myComponent = super.createComponent();
    }
    return myComponent;
  }

  public String getDisplayName() {
    return "HTTP Proxies Configuration";
  }

  @Override
  public String getHelpTopic() {
    return null;
  }

  private static String getNewName() {
    return "Unnamed";
  }

  private void addGroup(final ProxyGroup template) {
    final ProxyGroup group;
    if (template == null) {
      group = new ProxyGroup(getNewName(), "", ContainerUtil.newHashMap());
    } else {
      group = new ProxyGroup(getNewName(), template.getPatterns(), template.getProperties());
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
    final Ref<String> errorMessageRef = new Ref<>();
    final Set<String> checkSet = new HashSet<>();
    final AmbiguousPatternsFinder ambiguousPatternsFinder = new AmbiguousPatternsFinder();
    
    for (int i = 0; i < myRoot.getChildCount(); i++) {
      final MyNode node = (MyNode) myRoot.getChildAt(i);
      final GroupConfigurable groupConfigurable = (GroupConfigurable) node.getConfigurable();
      final String groupName = groupConfigurable.getEditableObject().getName();

      if (checkSet.contains(groupName)) {
        listener.onError(SvnBundle.message("dialog.edit.http.proxies.settings.error.same.group.names.text", groupName), myComponent, true);
        return false;
      }
      checkSet.add(groupName);
    }

    for (int i = 0; i < myRoot.getChildCount(); i++) {
      final MyNode node = (MyNode) myRoot.getChildAt(i);
      final GroupConfigurable groupConfigurable = (GroupConfigurable) node.getConfigurable();
      groupConfigurable.applyImpl();
      if(! groupConfigurable.validate(errorMessageRef)) {
        listener.onError(errorMessageRef.get(), myComponent, false);
        return false;
      }

      if (! groupConfigurable.getEditableObject().isDefault()) {
        final String groupName = groupConfigurable.getEditableObject().getName();
        final List<String> urls = groupConfigurable.getRepositories();
        ambiguousPatternsFinder.acceptUrls(groupName, urls);
      }
    }

    if(! ambiguousPatternsFinder.isValid(errorMessageRef)) {
      listener.onError(errorMessageRef.get(), myComponent, false);
      return false;
    }
    return true;
  }

  protected ArrayList<AnAction> createActions(final boolean fromPopup) {
    ArrayList<AnAction> result = new ArrayList<>();
    result.add(new AnAction("Add", "Add", IconUtil.getAddIcon()) {
        {
            registerCustomShortcutSet(CommonShortcuts.INSERT, myTree);
        }
        public void actionPerformed(AnActionEvent event) {
          addGroup(null);
        }


    });
    result.add(new MyDeleteAction(forAll(o -> {
      if (o instanceof MyNode) {
        final MyNode node = (MyNode)o;
        if (node.getConfigurable() instanceof GroupConfigurable) {
          final ProxyGroup group = ((GroupConfigurable)node.getConfigurable()).getEditableObject();
          return !group.isDefault();
        }
      }
      return false;
    })) {
      public void actionPerformed(final AnActionEvent e) {
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

    result.add(new AnAction("Copy", "Copy", PlatformIcons.COPY_ICON) {
        {
            registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_MASK)), myTree);
        }
        public void actionPerformed(AnActionEvent event) {
          // apply - for update of editable object
          try {
            getSelectedConfigurable().apply();
          } catch (ConfigurationException e) {
            // suppress & wait for OK
          }
          final ProxyGroup selectedGroup = (ProxyGroup) getSelectedObject();
          if (selectedGroup != null) {
            addGroup(selectedGroup);
          }
        }

        public void update(AnActionEvent event) {
            super.update(event);
            event.getPresentation().setEnabled(getSelectedObject() != null);
        }
    });
    return result;
  }

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
}

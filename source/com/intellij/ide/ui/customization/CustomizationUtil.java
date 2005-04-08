package com.intellij.ide.ui.customization;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil;
import com.intellij.openapi.keymap.impl.ui.Group;
import com.intellij.util.diff.Diff;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * User: anna
 * Date: Mar 30, 2005
 */
public class CustomizationUtil {
  public static ActionGroup correctActionGroup(final ActionGroup group, final CustomActionsSchema schema, final String defaultGroupName) {
    if (!schema.isCorrectActionGroup(group)){
       return group;
     }
    String text = group.getTemplatePresentation().getText();
    final int mnemonic = group.getTemplatePresentation().getMnemonic();
    if (text != null) {
      for (int i = 0; i < text.length(); i++) {
        if (Character.toUpperCase(text.charAt(i)) == mnemonic) {
          text = text.replaceFirst(String.valueOf(text.charAt(i)), "_" + text.charAt(i));
          break;
        }
      }
    }

    return new CashedAction(text, group.isPopup(), group, schema, defaultGroupName);
  }


  private static AnAction [] getReordableChildren(ActionGroup group, CustomActionsSchema schema, String defaultGroupName, AnActionEvent e, boolean forceUpdate) {
     String text = group.getTemplatePresentation().getText();
     ActionManager actionManager = ActionManager.getInstance();
     final ArrayList<AnAction> reordableChildren = new ArrayList<AnAction>();
     reordableChildren.addAll(Arrays.asList(group.getChildren(e)));
     final List<ActionUrl> actions = schema.getActions();
     for (Iterator<ActionUrl> iterator = actions.iterator(); iterator.hasNext();) {
       ActionUrl actionUrl = iterator.next();
       if ((actionUrl.getParentGroup().equals(text) ||
            actionUrl.getParentGroup().equals(defaultGroupName) ||
            actionUrl.getParentGroup().equals(actionManager.getId(group))) &&
           reordableChildren.size() > actionUrl.getAbsolutePosition()) {
         AnAction componentAction = actionUrl.getComponentAction();
         if (componentAction != null) {
           if (actionUrl.getActionType() == ActionUrl.ADDED) {
             reordableChildren.add(actionUrl.getAbsolutePosition(), componentAction);
           }
           else if (actionUrl.getActionType() == ActionUrl.DELETED) {
             final AnAction anAction = reordableChildren.get(actionUrl.getAbsolutePosition());
             //for unnamed groups
             if (anAction.getTemplatePresentation().getText() == null && anAction instanceof ActionGroup) {
               final Group eqGroup = ActionsTreeUtil.createGroup((ActionGroup)anAction, true);
               if (!actionUrl.getComponent().equals(eqGroup)) {
                 continue;
               }
             }
             if (anAction.getTemplatePresentation().getText() == null
                 ? componentAction.getTemplatePresentation().getText() != null
                 : !anAction.getTemplatePresentation().getText().equals(componentAction.getTemplatePresentation().getText())) {
               continue;
             }
             reordableChildren.remove(actionUrl.getAbsolutePosition());
           }
         }
       }
     }
     for (int i = 0; i < reordableChildren.size(); i++) {
       if (reordableChildren.get(i) instanceof ActionGroup) {
         final ActionGroup groupToCorrect = (ActionGroup)reordableChildren.get(i);
         final AnAction correctedAction = correctActionGroup(groupToCorrect, schema, "");
         reordableChildren.set(i, correctedAction);
       }
     }

     return reordableChildren.toArray(new AnAction[reordableChildren.size()]);
   }

  private static class CashedAction extends ActionGroup {
    private boolean myForceUpdate;
    private ActionGroup myGroup;
    private AnAction[] myChildren;
    private CustomActionsSchema mySchema;
    private String myDefaultGroupName;
    public CashedAction(String shortName, boolean popup, final ActionGroup group, CustomActionsSchema schema, String defaultGroupName) {
      super(shortName, popup);
      myGroup = group;
      mySchema = schema;
      myDefaultGroupName = defaultGroupName;
      myForceUpdate = true;
    }

    public AnAction[] getChildren(final AnActionEvent e) {
      if (myForceUpdate){
        myChildren = getReordableChildren(myGroup, mySchema, myDefaultGroupName, e, myForceUpdate);
        myForceUpdate = false;
        return myChildren;
      } else {
        if (!(myGroup instanceof DefaultActionGroup) || myChildren == null){
          myChildren = getReordableChildren(myGroup, mySchema, myDefaultGroupName, e, false);
        }
        return myChildren;
      }
    }
  }

  public static void optimizeSchema(final JTree tree, final CustomActionsSchema schema) {
    Group rootGroup = new Group("root", null, null);
    DefaultMutableTreeNode root = new DefaultMutableTreeNode(rootGroup);
    root.removeAllChildren();
    root.add(ActionsTreeUtil.createNode(ActionsTreeUtil.createMainMenuGroup(true)));
    root.add(ActionsTreeUtil.createNode(ActionsTreeUtil.createMainToolbarGroup()));
    root.add(ActionsTreeUtil.createNode(ActionsTreeUtil.createEditorPopupGroup()));
    final JTree defaultTree = new JTree(new DefaultTreeModel(root));

    final ArrayList<ActionUrl> addActions = new ArrayList<ActionUrl>();
    final ArrayList<ActionUrl> deleteActions = new ArrayList<ActionUrl>();

    TreeUtil.traverseDepth((TreeNode)tree.getModel().getRoot(), new TreeUtil.Traverse() {
      public boolean accept(Object node) {
        DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)node;
        if (treeNode.isLeaf()) {
          return true;
        }
        final ActionUrl url = getActionUrl(new TreePath(treeNode.getPath()), 0);
        url.getGroupPath().add(((Group)treeNode.getUserObject()).getName());
        final TreePath treePath = getTreePath(defaultTree, url);
        if (treePath != null) {
          final DefaultMutableTreeNode visited = (DefaultMutableTreeNode)treePath.getLastPathComponent();
          final ActionUrl[] defaultUserObjects = getChildUserObjects(visited, url);
          final ActionUrl[] currentUserObjects = getChildUserObjects(treeNode, url);
          Diff.Change change = Diff.buildChanges(defaultUserObjects, currentUserObjects);
          int deletedLines = 0;
          while (change != null) {
            for (int i = 0; i < change.deleted; i++) {
              final int idx = change.line0 + i;
              ActionUrl currentUserObject = defaultUserObjects[idx];
              currentUserObject.setActionType(ActionUrl.DELETED);
              currentUserObject.setAbsolutePosition(idx - deletedLines);
              deleteActions.add(currentUserObject);
              deletedLines++;
            }
            for (int i = 0; i < change.inserted; i++) {
              final int idx = change.line1 + i;
              ActionUrl currentUserObject = currentUserObjects[idx];
              currentUserObject.setActionType(ActionUrl.ADDED);
              currentUserObject.setAbsolutePosition(idx);
              addActions.add(currentUserObject);
            }
            change = change.link;
          }
        }
        return true;
      }
    });
    deleteActions.addAll(addActions);
    schema.setActions(deleteActions);
  }

  public static ActionUrl getActionUrl(final TreePath treePath, int actionType) {
    ActionUrl url = new ActionUrl();
    for (int i = 0; i < treePath.getPath().length - 1; i++) {
      Object o = ((DefaultMutableTreeNode)treePath.getPath()[i]).getUserObject();
      if (o instanceof Group) {
        url.getGroupPath().add(((Group)o).getName());
      }

    }

    final DefaultMutableTreeNode component = ((DefaultMutableTreeNode)treePath.getLastPathComponent());
    url.setComponent(component.getUserObject());
    DefaultMutableTreeNode node = component;
    final TreeNode parent = node.getParent();
    url.setAbsolutePosition(parent != null ? parent.getIndex(node) : 0);
    url.setActionType(actionType);
    return url;
  }


  public static TreePath getTreePath(JTree tree, ActionUrl url) {
    return getTreePath(0, url.getGroupPath(), tree.getModel().getRoot(), tree);
  }

  private static TreePath getTreePath(final int positionInPath, final List<String> path, final Object root, JTree tree) {
    if (!(root instanceof DefaultMutableTreeNode)) return null;

    final DefaultMutableTreeNode treeNode = ((DefaultMutableTreeNode)root);

    final Object userObject = treeNode.getUserObject();

    final String pathElement;
    if (path.size() > positionInPath) {
      pathElement = path.get(positionInPath);
    }
    else {
      return null;
    }

    if (pathElement == null) return null;

    if (!(userObject instanceof Group)) return null;

    if (!pathElement.equals(((Group)userObject).getName())) return null;


    TreePath currentPath = new TreePath(treeNode.getPath());

    if (positionInPath == path.size() - 1) {
      return currentPath;
    }

    for (int j = 0; j < treeNode.getChildCount(); j++) {
      final TreeNode child = treeNode.getChildAt(j);
      currentPath = getTreePath(positionInPath + 1, path, child, tree);
      if (currentPath != null) {
        break;
      }
    }

    return currentPath;
  }


  private static ActionUrl[] getChildUserObjects(DefaultMutableTreeNode node, ActionUrl parent) {
    ArrayList<ActionUrl> result = new ArrayList<ActionUrl>();
    ArrayList<String> groupPath = new ArrayList<String>();
    groupPath.addAll(parent.getGroupPath());
    for (int i = 0; i < node.getChildCount(); i++) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode)node.getChildAt(i);
      ActionUrl url = new ActionUrl();
      url.setGroupPath(groupPath);
      final Object userObject = child.getUserObject();
      url.setComponent(userObject);
      result.add(url);
    }
    return result.toArray(new ActionUrl[result.size()]);
  }

}

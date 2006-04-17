/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.errorTreeView;

import com.intellij.ide.errorTreeView.impl.ErrorTreeViewConfiguration;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Nov 12, 2004
 */
public class ErrorViewStructure extends AbstractTreeStructure {
  private final ErrorTreeElement myRoot = new MyRootElement();
  private final List<String> myGroupNames = new ArrayList<String>();
  private final Map<String, GroupingElement> myGroupNameToElementMap = new HashMap<String, GroupingElement>();
  private final Map<String, List<NavigatableMessageElement>> myGroupNameToMessagesMap = new HashMap<String, List<NavigatableMessageElement>>();
  private final Map<ErrorTreeElementKind, List<SimpleMessageElement>> mySimpleMessages = new HashMap<ErrorTreeElementKind, List<SimpleMessageElement>>();

  private static final ErrorTreeElementKind[] ourMessagesOrder = new ErrorTreeElementKind[] {
    ErrorTreeElementKind.INFO, ErrorTreeElementKind.ERROR, ErrorTreeElementKind.WARNING, ErrorTreeElementKind.GENERIC
  };
  private final Project myProject;
  private final boolean myCanHideWarnings;

  public ErrorViewStructure(Project project, final boolean canHideWarnings) {
    myProject = project;
    myCanHideWarnings = canHideWarnings;
  }

  public Object getRootElement() {
    return myRoot;
  }

  public Object[] getChildElements(Object element) {
    if (element == myRoot) {
      final List<Object> children = new ArrayList<Object>();
      // simple messages
      for (final ErrorTreeElementKind kind : ourMessagesOrder) {
        if (ErrorTreeElementKind.WARNING.equals(kind)) {
          if (myCanHideWarnings && ErrorTreeViewConfiguration.getInstance(myProject).isHideWarnings()) {
            continue;
          }
        }
        final List<SimpleMessageElement> elems = mySimpleMessages.get(kind);
        if (elems != null) {
          children.addAll(elems);
        }
      }
      // files
      for (final String myGroupName : myGroupNames) {
        final GroupingElement groupingElement = myGroupNameToElementMap.get(myGroupName);
        if (shouldShowFileElement(groupingElement)) {
          children.add(groupingElement);
        }
      }
      return children.toArray(new Object[children.size()]);
    }
    else if (element instanceof GroupingElement) {
      synchronized (myGroupNameToMessagesMap) {
        final List<NavigatableMessageElement> children = myGroupNameToMessagesMap.get(((GroupingElement)element).getName());
        if (children != null && children.size() > 0) {
          if (myCanHideWarnings && ErrorTreeViewConfiguration.getInstance(myProject).isHideWarnings()) {
            final List<NavigatableMessageElement> filtered = new ArrayList<NavigatableMessageElement>(children.size());
            for (final NavigatableMessageElement navigatableMessageElement : children) {
              if (ErrorTreeElementKind.WARNING.equals(navigatableMessageElement.getKind())) {
                continue;
              }
              filtered.add(navigatableMessageElement);
            }
            return filtered.toArray();
          }
          return children.toArray();
        }
      }
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  private boolean shouldShowFileElement(GroupingElement groupingElement) {
    if (!myCanHideWarnings || !ErrorTreeViewConfiguration.getInstance(myProject).isHideWarnings()) {
      return getChildCount(groupingElement) > 0;
    }
    synchronized (myGroupNameToMessagesMap) {
      final List<NavigatableMessageElement> children = myGroupNameToMessagesMap.get(groupingElement.getName());
      if (children != null) {
        for (final NavigatableMessageElement child : children) {
          if (!ErrorTreeElementKind.WARNING.equals(child.getKind())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public Object getParentElement(Object element) {
    if (element instanceof GroupingElement || element instanceof SimpleMessageElement) {
      return myRoot;
    }
    if (element instanceof NavigatableMessageElement) {
      return ((NavigatableMessageElement)element).getParent();
    }
    return null;
  }

  @NotNull
  public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
    return new ErrorTreeNodeDescriptor(myProject, parentDescriptor, (ErrorTreeElement)element);
  }

  public final void commit() {
  }

  public final boolean hasSomethingToCommit() {
    return false;
  }

  public void addMessage(ErrorTreeElementKind kind, String[] text, VirtualFile file, int line, int column, Object data) {
    if (file != null) {
      addNavigatableMessage(file.getPresentableUrl(), new OpenFileDescriptor(myProject, file, line, column), kind, text, data, NewErrorTreeViewPanel.createExportPrefix(line), NewErrorTreeViewPanel.createRendererPrefix(line, column));
    }
    else {
      addSimpleMessage(kind, text, data);
    }
  }

  public void addMessage(ErrorTreeElementKind kind, String[] text, Object data) {
    addSimpleMessage(kind, text, data);
  }

  public void addNavigatableMessage(String groupName, Navigatable navigatable, final ErrorTreeElementKind kind, final String[] message, final Object data, String exportText, String rendererTextPrefix) {
    synchronized (myGroupNameToMessagesMap) {
      List<NavigatableMessageElement> elements = myGroupNameToMessagesMap.get(groupName);
      if (elements == null) {
        elements = new ArrayList<NavigatableMessageElement>();
        myGroupNameToMessagesMap.put(groupName, elements);
      }
      elements.add(new NavigatableMessageElement(
        kind,
        getGroupingElement(groupName, data),
        message, navigatable, exportText, rendererTextPrefix)
      );
    }
  }
  public void clearGroupChildren(GroupingElement groupingElement) {
    synchronized (myGroupNameToMessagesMap) {
      List<NavigatableMessageElement> elements = myGroupNameToMessagesMap.get(groupingElement.getName());
      if (elements != null) {
        elements.clear();
      }
    }
  }

  private void addSimpleMessage(final ErrorTreeElementKind kind, final String[] text, final Object data) {
    List<SimpleMessageElement> elements = mySimpleMessages.get(kind);
    if (elements == null) {
      elements = new ArrayList<SimpleMessageElement>();
      mySimpleMessages.put(kind, elements);
    }
    elements.add(new SimpleMessageElement(kind, text, data));
  }

  public GroupingElement getGroupingElement(String groupName, Object data) {
    GroupingElement element = myGroupNameToElementMap.get(groupName);
    if (element == null) {
      element = new GroupingElement(groupName, data);
      myGroupNames.add(groupName);
      myGroupNameToElementMap.put(groupName, element);
    }
    return element;
  }

  public int getChildCount(GroupingElement groupingElement) {
    final List<NavigatableMessageElement> children = myGroupNameToMessagesMap.get(groupingElement.getName());
    return children == null ? 0 : children.size();
  }

  public void clear() {
    myGroupNames.clear();
    myGroupNameToElementMap.clear();
    myGroupNameToMessagesMap.clear();
    mySimpleMessages.clear();
  }

  public ErrorTreeElement getFirstMessage(ErrorTreeElementKind kind) {
    if (myCanHideWarnings && ErrorTreeElementKind.WARNING.equals(kind) && ErrorTreeViewConfiguration.getInstance(myProject).isHideWarnings()) {
      return null; // no warnings are available
    }
    final List<SimpleMessageElement> simpleMessages = mySimpleMessages.get(kind);
    if (simpleMessages != null && simpleMessages.size() > 0) {
      return simpleMessages.get(0);
    }
    for (final String path : myGroupNames) {
      synchronized (myGroupNameToMessagesMap) {
        final List<NavigatableMessageElement> messages = myGroupNameToMessagesMap.get(path);
        for (final NavigatableMessageElement navigatableMessageElement : messages) {
          if (kind.equals(navigatableMessageElement.getKind())) {
            return navigatableMessageElement;
          }
        }
      }
    }
    return null;
  }

  private static class MyRootElement extends ErrorTreeElement {
    public String[] getText() {
      return null;
    }

    public Object getData() {
      return null;
    }

    public String getExportTextPrefix() {
      return "";
    }
  }
}

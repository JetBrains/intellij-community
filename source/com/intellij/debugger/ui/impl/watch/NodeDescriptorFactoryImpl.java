package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.jdi.LocalVariableProxy;
import com.intellij.debugger.impl.descriptors.data.*;
import com.intellij.debugger.jdi.LocalVariableProxyImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadGroupReferenceProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.NodeDescriptorFactory;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.*;

import java.util.HashMap;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class NodeDescriptorFactoryImpl implements NodeDescriptorFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.impl.watch.NodeDescriptorFactoryImpl");
  private DescriptorTree myCurrentDescriptors = new DescriptorTree();

  private DescriptorTreeSearcher myDescriptorSearcher;
  private DescriptorTreeSearcher myDisplayDescriptorSearcher;

  protected final Project      myProject;

  public NodeDescriptorFactoryImpl(Project project) {
    myProject = project;
    myDescriptorSearcher        = new DescriptorTreeSearcher(new MarkedDescriptorTree());
    myDisplayDescriptorSearcher = new DisplayDescriptorTreeSearcher(new MarkedDescriptorTree());
  }

  private <T extends NodeDescriptorImpl> T getDescriptor(NodeDescriptorImpl parent, DescriptorData<T> data) {
    T descriptor = data.createDescriptor(myProject);

    T oldDescriptor = findDescriptor(parent, descriptor, data);

    if(oldDescriptor != null && oldDescriptor.getClass() == descriptor.getClass()) {
      descriptor.setAncestor(oldDescriptor);
    }
    else {
      T displayDescriptor = findDisplayDescriptor(parent, descriptor, data.getDisplayKey());
      if(displayDescriptor != null) {
        descriptor.displayAs(displayDescriptor);
      }
    }

    myCurrentDescriptors.addChild(parent, descriptor);

    return descriptor;
  }

  protected <T extends NodeDescriptorImpl>T findDisplayDescriptor(NodeDescriptorImpl parent, T descriptor, DisplayKey<T> key) {
    return myDisplayDescriptorSearcher.search(parent, descriptor, key);
  }

  protected <T extends NodeDescriptorImpl>T findDescriptor(NodeDescriptorImpl parent, T descriptor, DescriptorData<T> data) {
    return myDescriptorSearcher.search(parent, descriptor, data);
  }

  public DescriptorTree createHistoryTree() {
    return myCurrentDescriptors;
  }

  public void setHistoryTree(DescriptorTree tree) {
    final MarkedDescriptorTree descriptorTree = new MarkedDescriptorTree();
    tree.dfst(new DescriptorTree.DFSTWalker() {
      public void visit(NodeDescriptorImpl parent, NodeDescriptorImpl child) {
        descriptorTree.addChild(parent, child, DescriptorData.getDescriptorData(child));
      }
    });

    final MarkedDescriptorTree displayDescriptorTree = new MarkedDescriptorTree();
    tree.dfst(new DescriptorTree.DFSTWalker() {
      public void visit(NodeDescriptorImpl parent, NodeDescriptorImpl child) {
        displayDescriptorTree.addChild(parent, child, DescriptorData.getDescriptorData(child).getDisplayKey());
      }
    });

    myDescriptorSearcher = new DescriptorTreeSearcher(descriptorTree);
    myDisplayDescriptorSearcher = new DisplayDescriptorTreeSearcher(displayDescriptorTree);

    myCurrentDescriptors = new DescriptorTree();
  }

  public ArrayElementDescriptorImpl getArrayItemDescriptor(NodeDescriptor parent, ArrayReference array, int index) {
    return getDescriptor((NodeDescriptorImpl)parent, new ArrayItemData(array, index));
  }

  public FieldDescriptorImpl getFieldDescriptor(NodeDescriptor parent, ObjectReference objRef, Field field) {
    final DescriptorData<FieldDescriptorImpl> descriptorData;
    if (objRef == null ) {
      if (!field.isStatic()) {
        LOG.assertTrue(false, "Object reference is null for non-static field: " + field);
      }
      descriptorData = new StaticFieldData(field);
    }
    else {
      descriptorData = new FieldData(objRef, field);
    }
    return getDescriptor((NodeDescriptorImpl)parent, descriptorData);
  }

  public LocalVariableDescriptorImpl getLocalVariableDescriptor(NodeDescriptor parent, LocalVariableProxy local) {
    return getDescriptor((NodeDescriptorImpl)parent, new LocalData((LocalVariableProxyImpl)local));
  }

  public StackFrameDescriptorImpl getStackFrameDescriptor(NodeDescriptorImpl parent, StackFrameProxyImpl frameProxy) {
    return getDescriptor(parent, new StackFrameData(frameProxy));
  }

  public StaticDescriptorImpl getStaticDescriptor(NodeDescriptorImpl parent, ReferenceType refType) {//static is unique
    return getDescriptor(parent, new StaticData(refType));
  }

  public ValueDescriptorImpl getThisDescriptor(NodeDescriptorImpl parent, Value value) {
    return getDescriptor((NodeDescriptorImpl)parent, new ThisData(value));
  }

  public ThreadDescriptorImpl getThreadDescriptor(NodeDescriptorImpl parent, ThreadReferenceProxyImpl thread) {
    return getDescriptor((NodeDescriptorImpl)parent, new ThreadData(thread));
  }

  public ThreadGroupDescriptorImpl getThreadGroupDescriptor(NodeDescriptorImpl parent, ThreadGroupReferenceProxyImpl group) {
    return getDescriptor((NodeDescriptorImpl)parent, new ThreadGroupData(group));
  }

  public UserExpressionDescriptorImpl getUserExpressionDescriptor(ValueDescriptor parent, String typeName, String name, TextWithImports expression) {
    return getDescriptor((NodeDescriptorImpl)parent, new UserExpressionData((ValueDescriptorImpl)parent, typeName, name, (TextWithImportsImpl)expression));
  }

  private static class DescriptorTreeSearcher {
    private final MarkedDescriptorTree myDescriportTree;

    private final HashMap<NodeDescriptorImpl, NodeDescriptorImpl> mySearchedDescriptors = new HashMap<NodeDescriptorImpl, NodeDescriptorImpl>();

    public DescriptorTreeSearcher(MarkedDescriptorTree descriportTree) {
      myDescriportTree = descriportTree;
    }

    public <T extends NodeDescriptorImpl> T search(NodeDescriptorImpl parent, T descriptor, DescriptorKey<T> key) {
      T result = searchImpl(parent, key);
      if(result != null) {
        mySearchedDescriptors.put(descriptor, result);
      }
      return result;
    }

    private <T extends NodeDescriptorImpl> T searchImpl(NodeDescriptorImpl parent, DescriptorKey<T> key) {
      if(parent == null) {
        return myDescriportTree.getChild(null, key);
      }
      else {
        NodeDescriptorImpl parentDescriptor = getSearched(parent);
        return parentDescriptor != null ? myDescriportTree.getChild(parentDescriptor, key) : null;
      }
    }

    protected NodeDescriptorImpl getSearched(NodeDescriptorImpl parent) {
      return mySearchedDescriptors.get(parent);
    }
  }

  private class DisplayDescriptorTreeSearcher extends DescriptorTreeSearcher {
    public DisplayDescriptorTreeSearcher(MarkedDescriptorTree descriportTree) {
      super(descriportTree);
    }

    protected NodeDescriptorImpl getSearched(NodeDescriptorImpl parent) {
      NodeDescriptorImpl searched = super.getSearched(parent);
      if(searched == null) {
        return myDescriptorSearcher.getSearched(parent);
      }
      return searched;
    }
  }
}

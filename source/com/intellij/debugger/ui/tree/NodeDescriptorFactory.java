package com.intellij.debugger.ui.tree;

import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.jdi.LocalVariableProxy;
import com.intellij.debugger.ui.impl.watch.UserExpressionDescriptorImpl;
import com.intellij.debugger.impl.descriptors.data.DescriptorData;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.Field;
import com.sun.jdi.ObjectReference;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

/**
 * creates descriptors
 * if descriptor was already created in current context (that is location in debugee code) returns that descriptor
 * else creates new descriptor and restores it's representation properties from history
 */

public interface NodeDescriptorFactory {
  ArrayElementDescriptor getArrayItemDescriptor(NodeDescriptor parent, ArrayReference array, int index);

  FieldDescriptor getFieldDescriptor(NodeDescriptor parent, ObjectReference objRef, Field field);

  LocalVariableDescriptor getLocalVariableDescriptor(NodeDescriptor parent, LocalVariableProxy local);

  UserExpressionDescriptor getUserExpressionDescriptor(NodeDescriptor parent, final DescriptorData<UserExpressionDescriptor> data);
}

package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public abstract class DescriptorData <T extends NodeDescriptorImpl> implements DescriptorKey<T>{
  private static final Key DESCRIPTOR_DATA = new Key("DESCRIPTOR_DATA");

  private final Class<? extends NodeDescriptorImpl> myDescriptorClass;

  protected DescriptorData(Class<? extends NodeDescriptorImpl> descriptorClass) {
    myDescriptorClass = descriptorClass;
  }

  public T createDescriptor(Project project) {
    T descriptor = createDescriptorImpl(project);
    descriptor.putUserData(DESCRIPTOR_DATA, this);
    return descriptor;
  }

  protected abstract T createDescriptorImpl(Project project);

  public abstract boolean equals(Object object);

  public abstract int hashCode();

  public abstract DisplayKey<T> getDisplayKey();

  public static <T extends NodeDescriptorImpl> DescriptorData<T> getDescriptorData(T descriptor) {
    return (DescriptorData<T>)descriptor.getUserData(DESCRIPTOR_DATA);
  }
}

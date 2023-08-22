// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.stubs;

import com.intellij.psi.stubs.ObjectStubSerializer;
import com.intellij.psi.stubs.Stub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public final class AttributeStub extends DomStub {
  private final String myValue;

  public AttributeStub(DomStub parent, @NotNull String name, @Nullable String namespace, @NotNull String value) {
    super(parent, name, namespace);
    myValue = value;
  }

  @NotNull
  public String getValue() {
    return myValue;
  }

  @NotNull
  @Override
  public List<DomStub> getChildrenStubs() {
    return Collections.emptyList();
  }

  @Override
  public int getIndex() {
    return 0;
  }

  @Override
  public ObjectStubSerializer<?, Stub> getStubType() {
    return DomElementTypeHolder.AttributeStub;
  }

  @Override
  public String toString() {
    return getName() + ":" + getValue();
  }
}

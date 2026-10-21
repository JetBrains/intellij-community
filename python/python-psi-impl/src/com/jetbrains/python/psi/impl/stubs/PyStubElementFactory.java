// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubRegistry;
import com.intellij.psi.stubs.StubSerializingElementFactory;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.psi.PyElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * The stub factory and serializer of one Python element type. {@link #register} binds it to the element type that
 * {@link #getStubElementType()} returns. The external id is {@code py.} plus the debug name of that type, which keeps
 * the ids of the stubs that the former {@code IStubElementType} subclasses wrote.
 */
@ApiStatus.Internal
public abstract class PyStubElementFactory<StubT extends StubElement<?>, PsiT extends PyElement>
  implements StubSerializingElementFactory<StubT, PsiT> {

  /** The element type whose stubs this factory creates. A dialect returns its own element type. */
  protected abstract @NotNull IElementType getStubElementType();

  /** Binds this factory to its element type in {@code registry}. */
  public final void register(@NotNull StubRegistry registry) {
    registry.registerStubSerializingFactory(getStubElementType(), this);
  }

  @Override
  public @NotNull String getExternalId() {
    return "py." + getStubElementType().getDebugName();
  }

  @Override
  public void indexStub(@NotNull StubT stub, @NotNull IndexSink sink) {
  }
}

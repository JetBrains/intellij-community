// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyStubElementTypes;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyPsiCreatingElementType;
import com.jetbrains.python.psi.PyStubElementType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapts a {@link PyStubElementFactory} to the legacy {@link PyStubElementType} for the deprecated constants of
 * {@code PyStubElementTypes}. The adapter is a separate {@link IElementType} and never appears in a tree. It delegates the PSI
 * creation to the plain element type and the stub work to the factory.
 * <p>
 * The external id is {@link #EXTERNAL_ID_PREFIX} plus the debug name. The {@code stubElementTypeHolder} registration of
 * {@code PyStubElementTypes} declares the same prefix, so the platform accepts the adapter after the index initialization.
 * <p>
 * {@link #forElementType} gives the adapter of a plain element type. The deprecated {@code getElementType()} of a PSI element
 * and {@code getStubType()} of a stub return it, so a plugin on the old API gets the object it compares with.
 */
@ApiStatus.Internal
public final class PyLegacyStubElementType<StubT extends StubElement<?>, PsiT extends PyElement> extends PyStubElementType<StubT, PsiT> {
  public static final @NotNull String EXTERNAL_ID_PREFIX = "py.legacy.";

  /** The adapters by their plain element type. Each constructor adds its adapter. */
  private static final Map<IElementType, PyLegacyStubElementType<?, ?>> ADAPTERS = new ConcurrentHashMap<>();

  private final @NotNull PyPsiCreatingElementType myElementType;
  private final @NotNull PyStubElementFactory<StubT, PsiT> myFactory;

  /**
   * @param elementType the plain element type of {@code PyElementTypes} that the factory is registered for
   * @param factory     the stub factory of that element type
   */
  public PyLegacyStubElementType(@NotNull IElementType elementType, @NotNull PyStubElementFactory<StubT, PsiT> factory) {
    super(elementType.getDebugName());
    myElementType = (PyPsiCreatingElementType)elementType;
    myFactory = factory;
    ADAPTERS.put(elementType, this);
  }

  /**
   * The adapter of {@link PyStubElementTypes} for the plain element type {@code elementType}, or {@code null} when
   * {@code PyStubElementTypes} has no constant for it.
   */
  @SuppressWarnings("removal")
  public static @Nullable PyLegacyStubElementType<?, ?> forElementType(@NotNull IElementType elementType) {
    // The constants of PyStubElementTypes register themselves at the initialization of the interface. One read initializes it.
    Objects.requireNonNull(PyStubElementTypes.CLASS_DECLARATION);
    return ADAPTERS.get(elementType);
  }

  @Override
  public @NotNull String getExternalId() {
    // The IStubElementType constructor calls this method before the fields are set. Use the debug name only.
    return EXTERNAL_ID_PREFIX + getDebugName();
  }

  @Override
  public @NotNull PsiElement createElement(@NotNull ASTNode node) {
    return myElementType.createElement(node);
  }

  @Override
  public PsiT createPsi(@NotNull StubT stub) {
    return myFactory.createPsi(stub);
  }

  @Override
  public @NotNull StubT createStub(@NotNull PsiT psi, StubElement<? extends PsiElement> parentStub) {
    return myFactory.createStub(psi, parentStub);
  }

  @Override
  public boolean shouldCreateStub(ASTNode node) {
    return myFactory.shouldCreateStub(node);
  }

  @Override
  public void serialize(@NotNull StubT stub, @NotNull StubOutputStream dataStream) throws IOException {
    myFactory.serialize(stub, dataStream);
  }

  @Override
  public @NotNull StubT deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return myFactory.deserialize(dataStream, parentStub);
  }

  @Override
  public void indexStub(@NotNull StubT stub, @NotNull IndexSink sink) {
    myFactory.indexStub(stub, sink);
  }
}

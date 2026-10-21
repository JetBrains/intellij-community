// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.tree.IStubFileElementType;
import com.jetbrains.python.ast.impl.PyAstFileElementType;
import com.jetbrains.python.psi.impl.stubs.PyFileStubBuilder;
import com.jetbrains.python.psi.impl.stubs.PyFileStubSerializer;
import com.jetbrains.python.psi.impl.stubs.PyLanguageStubDefinition;
import com.jetbrains.python.psi.stubs.PyFileStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * The stub file element type of a Python dialect that keeps its own stub file type, such as Cython or a Python sub-language.
 * The Python and the PythonStub languages use the plain file node types of their parser definitions instead, with the
 * stub support that {@code PyStubRegistryExtension} and {@code PyLanguageStubDefinition} register.
 */
public class PyFileElementType extends IStubFileElementType<PyFileStub> {
  protected PyFileElementType(Language language) {
    super(language);
  }

  @Override
  public StubBuilder getBuilder() {
    return new PyFileStubBuilder();
  }

  @Override
  public int getStubVersion() {
    return PyLanguageStubDefinition.STUB_VERSION;
  }

  @Override
  public @Nullable ASTNode parseContents(@NotNull ASTNode node) {
    return PyAstFileElementType.parseFileContents(this, node);
  }

  @Override
  public @NotNull String getExternalId() {
    return "python.FILE";
  }

  @Override
  public void serialize(@NotNull PyFileStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    PyFileStubSerializer.write(stub, dataStream);
  }

  @Override
  public @NotNull PyFileStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return PyFileStubSerializer.read(dataStream, this);
  }

  public static void writeNullableList(StubOutputStream dataStream, final List<String> names) throws IOException {
    if (names == null) {
      dataStream.writeBoolean(false);
    }
    else {
      dataStream.writeBoolean(true);
      dataStream.writeVarInt(names.size());
      for (String name : names) {
        dataStream.writeName(name);
      }
    }
  }

  public static @Nullable List<String> readNullableList(StubInputStream dataStream) throws IOException {
    boolean hasNames = dataStream.readBoolean();
    List<String> names = null;
    if (hasNames) {
      int size = dataStream.readVarInt();
      names = new ArrayList<>(size);
      for (int i = 0; i < size; i++) {
        names.add(dataStream.readNameString());
      }
    }
    return names;
  }
}

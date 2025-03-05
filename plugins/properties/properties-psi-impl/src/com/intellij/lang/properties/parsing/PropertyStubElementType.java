// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.lang.properties.parsing;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.LighterASTTokenNode;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.PropertyKeyIndex;
import com.intellij.lang.properties.psi.PropertyStub;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lang.properties.psi.impl.PropertyStubImpl;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.stubs.*;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

class PropertyStubElementType extends ILightStubElementType<PropertyStub, Property> {
  PropertyStubElementType() {
    super("PROPERTY", PropertiesLanguage.INSTANCE);
  }

  @Override
  public Property createPsi(final @NotNull PropertyStub stub) {
    return new PropertyImpl(stub, this);
  }

  @Override
  public @NotNull PropertyStub createStub(final @NotNull Property psi, final StubElement parentStub) {
    return new PropertyStubImpl(parentStub, psi.getKey());
  }

  @Override
  public @NotNull String getExternalId() {
    return "properties.prop";
  }

  @Override
  public void serialize(final @NotNull PropertyStub stub, final @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getKey());
  }

  @Override
  public @NotNull PropertyStub deserialize(final @NotNull StubInputStream dataStream, final StubElement parentStub) throws IOException {
    return new PropertyStubImpl(parentStub, dataStream.readNameString());
  }

  @Override
  public void indexStub(final @NotNull PropertyStub stub, final @NotNull IndexSink sink) {
    sink.occurrence(PropertyKeyIndex.KEY, PropertyImpl.unescape(stub.getKey()));
  }

  @Override
  public @NotNull PropertyStub createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    LighterASTNode keyNode = LightTreeUtil.firstChildOfType(tree, node, PropertiesTokenTypes.KEY_CHARACTERS);
    String key = intern(tree.getCharTable(), keyNode);
    return new PropertyStubImpl(parentStub, key);
  }
  
  public static String intern(@NotNull CharTable table, @NotNull LighterASTNode node) {
    assert node instanceof LighterASTTokenNode : node;
    return table.intern(((LighterASTTokenNode)node).getText()).toString();
  }
}

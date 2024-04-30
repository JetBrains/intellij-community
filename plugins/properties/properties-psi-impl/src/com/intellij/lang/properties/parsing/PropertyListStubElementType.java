// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.lang.properties.parsing;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.lang.properties.psi.PropertiesList;
import com.intellij.lang.properties.psi.PropertiesListStub;
import com.intellij.lang.properties.psi.impl.PropertiesListImpl;
import com.intellij.lang.properties.psi.impl.PropertiesListStubImpl;
import com.intellij.psi.stubs.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

@ApiStatus.Internal
public class PropertyListStubElementType extends ILightStubElementType<PropertiesListStub, PropertiesList> {
  PropertyListStubElementType() {
    super("PROPERTIES_LIST", PropertiesLanguage.INSTANCE);
  }

  @Override
  public PropertiesList createPsi(final @NotNull PropertiesListStub stub) {
    return new PropertiesListImpl(stub);
  }

  @Override
  public @NotNull PropertiesListStub createStub(final @NotNull PropertiesList psi, final StubElement parentStub) {
    return new PropertiesListStubImpl(parentStub);
  }

  @Override
  public @NotNull String getExternalId() {
    return "properties.propertieslist";
  }

  @Override
  public void serialize(final @NotNull PropertiesListStub stub, final @NotNull StubOutputStream dataStream) throws IOException {
  }

  @Override
  public @NotNull PropertiesListStub deserialize(final @NotNull StubInputStream dataStream, final StubElement parentStub) throws IOException {
    return new PropertiesListStubImpl(parentStub);
  }

  @Override
  public void indexStub(final @NotNull PropertiesListStub stub, final @NotNull IndexSink sink) {
  }

  @Override
  public @NotNull PropertiesListStub createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    return new PropertiesListStubImpl(parentStub);
  }
}
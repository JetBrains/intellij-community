/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author max
 */
package com.intellij.lang.properties.parsing;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.LighterASTTokenNode;
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

public class PropertyStubElementType extends ILightStubElementType<PropertyStub, Property> {
  public PropertyStubElementType() {
    super("PROPERTY", PropertiesElementTypes.LANG);
  }

  public Property createPsi(@NotNull final PropertyStub stub) {
    return new PropertyImpl(stub, this);
  }

  @NotNull
  public PropertyStub createStub(@NotNull final Property psi, final StubElement parentStub) {
    return new PropertyStubImpl(parentStub, psi.getKey());
  }

  @Override
  @NotNull
  public String getExternalId() {
    return "properties.prop";
  }

  public void serialize(@NotNull final PropertyStub stub, @NotNull final StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getKey());
  }

  @NotNull
  public PropertyStub deserialize(@NotNull final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    return new PropertyStubImpl(parentStub, dataStream.readNameString());
  }

  public void indexStub(@NotNull final PropertyStub stub, @NotNull final IndexSink sink) {
    sink.occurrence(PropertyKeyIndex.KEY, PropertyImpl.unescape(stub.getKey()));
  }

  @Override
  public PropertyStub createStub(LighterAST tree, LighterASTNode node, StubElement parentStub) {
    LighterASTNode keyNode = LightTreeUtil.firstChildOfType(tree, node, PropertiesTokenTypes.KEY_CHARACTERS);
    String key = intern(tree.getCharTable(), keyNode);
    return new PropertyStubImpl(parentStub, key);
  }
  
  public static String intern(@NotNull CharTable table, @NotNull LighterASTNode node) {
    assert node instanceof LighterASTTokenNode : node;
    return table.intern(((LighterASTTokenNode)node).getText()).toString();
  }
}

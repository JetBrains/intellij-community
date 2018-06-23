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
import com.intellij.lang.properties.psi.PropertiesList;
import com.intellij.lang.properties.psi.PropertiesListStub;
import com.intellij.lang.properties.psi.impl.PropertiesListImpl;
import com.intellij.lang.properties.psi.impl.PropertiesListStubImpl;
import com.intellij.psi.stubs.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class PropertyListStubElementType extends ILightStubElementType<PropertiesListStub, PropertiesList> {
  public PropertyListStubElementType() {
    super("PROPERTIES_LIST", PropertiesElementTypes.LANG);
  }

  public PropertiesList createPsi(@NotNull final PropertiesListStub stub) {
    return new PropertiesListImpl(stub);
  }

  @NotNull
  public PropertiesListStub createStub(@NotNull final PropertiesList psi, final StubElement parentStub) {
    return new PropertiesListStubImpl(parentStub);
  }

  @Override
  @NotNull
  public String getExternalId() {
    return "properties.propertieslist";
  }

  public void serialize(@NotNull final PropertiesListStub stub, @NotNull final StubOutputStream dataStream) throws IOException {
  }

  @NotNull
  public PropertiesListStub deserialize(@NotNull final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    return new PropertiesListStubImpl(parentStub);
  }

  public void indexStub(@NotNull final PropertiesListStub stub, @NotNull final IndexSink sink) {
  }

  @Override
  public PropertiesListStub createStub(LighterAST tree, LighterASTNode node, StubElement parentStub) {
    return new PropertiesListStubImpl(parentStub);
  }
}
/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.xml.stubs;

import com.intellij.psi.stubs.ObjectStubSerializer;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class AttributeStub extends DomStub {

  private final String myValue;

  public AttributeStub(DomStub parent, StringRef name, StringRef namespace, String value) {
    super(parent, name, namespace);
    myValue = value;
  }

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
  public ObjectStubSerializer getStubType() {
    return AttributeStubSerializer.INSTANCE;
  }

  @Override
  public String toString() {
    return getName() + ":" + getValue();
  }
}

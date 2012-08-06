/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.psi.stubs.Stub;
import com.intellij.util.SmartList;

import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 8/2/12
 */
public class TagStub extends DomStub {

  private final List<DomStub> myChildren = new SmartList<DomStub>();

  public TagStub(DomStub parent, String name) {
    super(parent, name);
  }

  void addChild(DomStub child) {
    myChildren.add(child);
  }

  @Override
  public List<? extends Stub> getChildrenStubs() {
    return myChildren;
  }

  @Override
  public ObjectStubSerializer getStubType() {
    return TagStubSerializer.INSTANCE;
  }
}

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
package com.intellij.designer.designSurface;

import com.intellij.designer.model.RadComponent;

import java.util.Collections;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public abstract class AbstractEditOperation implements EditOperation {
  protected final RadComponent myContainer;
  protected final OperationContext myContext;
  protected List<RadComponent> myComponents;

  public AbstractEditOperation(RadComponent container, OperationContext context) {
    myContainer = container;
    myContext = context;
  }

  @Override
  public void setComponent(RadComponent component) {
    myComponents = Collections.singletonList(component);
  }

  @Override
  public void setComponents(List<RadComponent> components) {
    myComponents = components;
  }

  @Override
  public boolean canExecute() {
    return true;
  }
}
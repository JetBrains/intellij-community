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
package com.intellij.util.xml.ui;

import com.intellij.openapi.util.Disposer;

import java.util.ArrayList;
import java.util.List;

/**
 * author: lesya
 */
public class CompositeCommittable implements Committable, Highlightable {
  private final List<Committable> myComponents = new ArrayList<>();

  public final <T extends Committable> T addComponent(T panel) {
    myComponents.add(panel);
    Disposer.register(this, panel);
    return panel;
  }

  @Override
  public void commit() {
    for (final Committable committable : myComponents) {
      committable.commit();
    }
  }

  @Override
  public void reset() {
    for (final Committable committable : myComponents) {
      committable.reset();
    }
  }

  @Override
  public void dispose() {
  }

  public List<Committable> getChildren() {
    return myComponents;
  }

  @Override
  public void updateHighlighting() {
    for (final Committable component : myComponents) {
      CommittableUtil.updateHighlighting(component);
    }
  }
}

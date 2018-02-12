/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.uiDesigner.propertyInspector;

import com.intellij.uiDesigner.radComponents.RadRootContainer;

import javax.swing.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public interface PropertyRenderer<V> {
  /**
   * @return {@code JComponent} to represent the {@code value}
   * somewhere in UI (for example in the JList of in the JTree). To be
   * consistent with other UI additional parameter abount selection and
   * focus are also passed.
   */
  JComponent getComponent(RadRootContainer rootContainer, V value, boolean selected, boolean hasFocus);

  /**
   * Renderer should update UI of all its internal components to fit current
   * IDEA Look And Feel. We cannot directly update UI of the component
   * that is returned by {@link #getComponent } method
   * because hidden component that are not in the Swing tree can exist.
   */
  void updateUI();
}

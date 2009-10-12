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
package com.intellij.util.xml.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Base component to be associated with (bound to) DOM controls (see {@link com.intellij.util.xml.ui.DomUIControl})
 * that have {@link com.intellij.openapi.editor.Editor}
 * as their part. This includes simple text, PSI class, PSI type editing, etc. The controls themselves
 * can be created in {@link com.intellij.util.xml.ui.DomUIFactory}.
 *
 * Adds an empty disable JTextField to itself to be visible if unbound (or in UI designer), which
 * should be removed on binding.
 *
 * @author peter
 */
public class EditorContainerPanel extends JPanel {
  public EditorContainerPanel() {
    super(new BorderLayout());
    setFocusable(false);
    final JTextField field = new JTextField();
    field.setEnabled(false);
    add(field);
  }
}

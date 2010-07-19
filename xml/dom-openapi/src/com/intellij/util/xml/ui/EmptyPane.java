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

import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/** 
 * @author cdr
 */

public class EmptyPane {
  private JPanel myPanel;
  private JLabel myLabel;

  public EmptyPane(String text) {
    final Color color = UIUtil.getSeparatorShadow();
    myLabel.setForeground(color);
    myLabel.setText(text);
    myPanel.setBackground(new Tree().getBackground());
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public void setText(String text) {
    myLabel.setText(text);
  }

  public static void addToPanel(JPanel panel, String text) {
    final EmptyPane emptyPane = new EmptyPane(text);
    panel.setLayout(new BorderLayout());
    panel.add(emptyPane.getComponent(), BorderLayout.CENTER);
  }

}

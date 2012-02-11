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

import com.intellij.designer.componentTree.TreeComponentDecorator;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public abstract class DesignerEditorPanel extends JPanel implements DataProvider {
  protected RadComponent myRootComponent;

  public DesignerEditorPanel(@NotNull Module module, @NotNull VirtualFile file) {
    setLayout(new BorderLayout());
    add(new JLabel("Design Surface", JLabel.CENTER), BorderLayout.CENTER);
  }

  @Override
  public Object getData(@NonNls String dataId) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public void dispose() {
    // TODO: Auto-generated method stub
  }

  public JComponent getPreferredFocusedComponent() {
    return null;  // TODO: Auto-generated method stub
  }

  public Object[] getTreeRoots() {
    return myRootComponent == null ? ArrayUtil.EMPTY_OBJECT_ARRAY : new Object[]{myRootComponent};
  }

  public abstract TreeComponentDecorator getTreeDecorator();
}
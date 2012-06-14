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

package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.ModuleProvider;
import com.intellij.uiDesigner.palette.Palette;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/**
 * @author yole
 */
public class RadTable extends RadAtomicComponent {
  public static class Factory extends RadComponentFactory {
    public RadComponent newInstance(ModuleProvider module, Class aClass, String id) {
      return new RadTable(module, aClass, id);
    }

    public RadComponent newInstance(final Class componentClass, final String id, final Palette palette) {
      return new RadTable(componentClass, id, palette);
    }
  }

  public RadTable(final ModuleProvider module, final Class componentClass, final String id) {
    super(module, componentClass, id);
    initDefaultModel();
  }

  public RadTable(final Class componentClass, final String id, final Palette palette) {
    super(componentClass, id, palette);
    initDefaultModel();
  }

  private void initDefaultModel() {
    @NonNls Object[][] data = new Object[][] { new Object[] { "round", "red"},
      new Object[] { "square", "green" } };
    @NonNls Object[] columnNames = new Object[] { "Shape", "Color" };
    try {
      ((JTable) getDelegee()).setModel(new DefaultTableModel(data, columnNames));
    }
    catch(Exception ex) {
      // a custom table subclass may not like our model, so ignore the exception if thrown here
    }
    catch(AssertionError ex) {
      // a custom table subclass may not like our model, so ignore the exception if thrown here
    }
  }
}

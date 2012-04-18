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
package com.intellij.designer.palette;

import com.intellij.designer.model.MetaManager;
import com.intellij.ide.palette.PaletteGroup;
import com.intellij.ide.palette.PaletteItemProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * @author Alexander Lobas
 */
public abstract class AbstractPaletteProvider implements PaletteItemProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.designer.palette.AbstractPaletteProvider");

  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);

  @Override
  public PaletteGroup[] getActiveGroups(VirtualFile virtualFile) {
    if (accept(virtualFile)) {
      MetaManager manager = getMetaManager();
      if (manager != null) {
        manager.setPaletteChangeSupport(myPropertyChangeSupport);
        return manager.getPaletteGroups();
      }
      LOG.error("VirtualFile: " + virtualFile + " accepted but MetaManager is null");
    }
    return PaletteGroup.EMPTY_ARRAY;
  }

  protected abstract boolean accept(VirtualFile virtualFile);

  protected abstract MetaManager getMetaManager();

  @Override
  public void addListener(PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }

  @Override
  public void removeListener(PropertyChangeListener listener) {
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }
}
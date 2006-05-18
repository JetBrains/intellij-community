/*
 * Copyright 2000-2006 JetBrains s.r.o.
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

package com.intellij.ide.ui;

import com.intellij.openapi.application.ApplicationManager;

import javax.swing.*;

/**
 * User: anna
 * Date: 17-May-2006
 */
public abstract class LafManager {
  public static LafManager getInstance(){
    return ApplicationManager.getApplication().getComponent(LafManager.class);
  }

  public abstract UIManager.LookAndFeelInfo[] getInstalledLookAndFeels();

  public abstract UIManager.LookAndFeelInfo getCurrentLookAndFeel();

  public abstract boolean isUnderAquaLookAndFeel();

  public abstract boolean isUnderQuaquaLookAndFeel();

  public abstract void setCurrentLookAndFeel(UIManager.LookAndFeelInfo lookAndFeelInfo);

  public abstract void updateUI();

  public abstract void repaintUI();

  public abstract void addLafManagerListener(LafManagerListener l);

  public abstract void removeLafManagerListener(LafManagerListener l);
}

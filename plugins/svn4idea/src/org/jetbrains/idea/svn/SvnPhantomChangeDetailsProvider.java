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
package org.jetbrains.idea.svn;

import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.RefreshablePanel;
import com.intellij.openapi.vcs.changes.VcsChangeDetailsProvider;
import com.intellij.vcsUtil.UIVcsUtil;
import com.intellij.vcsUtil.VcsUtil;

import javax.swing.*;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 5/17/12
 * Time: 12:19 PM
 */
public class SvnPhantomChangeDetailsProvider implements VcsChangeDetailsProvider {
  @Override
  public String getName() {
    return "Phantom Change";
  }

  @Override
  public boolean canComment(Change change) {
    return change.isPhantom();
  }

  @Override
  public RefreshablePanel comment(Change change, JComponent parent, BackgroundTaskQueue queue) {
    return new RefreshablePanel() {
      @Override
      public boolean refreshDataSynch() {
        return true;
      }

      @Override
      public void dataChanged() {
      }

      @Override
      public void refresh() {
      }

      @Override
      public JPanel getPanel() {
        return UIVcsUtil.infoPanel("Technical record", "This change is recorded because its target file was deleted,\nand some parent directory was copied (or moved) into the new place.");
      }

      @Override
      public void away() {
      }

      @Override
      public boolean isStillValid(Object o) {
        return ((Change) o).isPhantom();
      }

      @Override
      public void dispose() {
      }
    };
  }
}

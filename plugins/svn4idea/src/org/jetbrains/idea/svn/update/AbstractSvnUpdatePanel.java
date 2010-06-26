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
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.util.FilePathByPathComparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.DepthCombo;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import javax.swing.*;
import java.awt.*;
import java.util.*;

public abstract class AbstractSvnUpdatePanel {
  protected final SvnVcs myVCS;

  private final Map<FilePath, SvnPanel> myRootToPanel = new LinkedHashMap<FilePath, SvnPanel>();

  public AbstractSvnUpdatePanel(final SvnVcs vcs) {
    myVCS = vcs;
  }

  protected void init(final Collection<FilePath> roots) {
    final JPanel configureRootsPanel = getRootsPanel();
    configureRootsPanel.setLayout(new BorderLayout());

    final ArrayList<FilePath> rootsCopy = new ArrayList<FilePath>(roots);
    Collections.sort(rootsCopy, FilePathByPathComparator.getInstance());
    for (FilePath root : rootsCopy) {
      SVNURL url = getUrlFor(root);
      if (url != null) {
        myRootToPanel.put(root, createRootPanel(root, myVCS, roots));
      }

      if (myRootToPanel.size() == 1) {

        configureRootsPanel.add(myRootToPanel.values().iterator().next().getPanel(), BorderLayout.CENTER);
      }
      else {
        final MultipleRootsEditor multipleRootsEditor = new MultipleRootsEditor(myRootToPanel, myVCS.getProject());
        configureRootsPanel.add(multipleRootsEditor.getPanel(), BorderLayout.CENTER);
      }
    }
  }

  protected abstract SvnPanel createRootPanel(final FilePath root, final SvnVcs p1, Collection<FilePath> roots);

  protected abstract JPanel getRootsPanel();

  public void reset(final SvnConfiguration configuration) {
    getStatusBox().setSelected(configuration.UPDATE_RUN_STATUS);
    getDepthBox().setSelectedItem(configuration.UPDATE_DEPTH);

    for (FilePath filePath : myRootToPanel.keySet()) {
      myRootToPanel.get(filePath).reset(configuration);
    }

  }

  public void apply(final SvnConfiguration configuration) throws ConfigurationException {
    configuration.UPDATE_RUN_STATUS = getStatusBox().isSelected();
    configuration.UPDATE_DEPTH = getDepthBox().getSelectedItem();

    for (FilePath filePath : myRootToPanel.keySet()) {
      final SvnPanel svnPanel = myRootToPanel.get(filePath);
      if (svnPanel.canApply()) {
        svnPanel.apply(configuration);
      }
    }
  }

  @Nullable
  private SVNURL getUrlFor(@NotNull final FilePath root) {
    try {
      SVNWCClient wcClient = myVCS.createWCClient();
      final SVNInfo info = wcClient.doInfo(root.getIOFile(), SVNRevision.WORKING);
      if (info != null) {
        return info.getURL();
      }
      return null;
    }
    catch (SVNException e) {
      return null;
    }
  }

  protected abstract JComponent getPanel();

  protected abstract JCheckBox getStatusBox();

  protected abstract DepthCombo getDepthBox();
}

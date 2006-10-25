/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class AbstractSvnUpdatePanel {

  @NonNls public static final String HEAD_REVISION = "HEAD";

  protected final SvnVcs myVCS;

  private Map<FilePath, SvnPanel> myRootToPanel = new LinkedHashMap<FilePath, SvnPanel>();

  public AbstractSvnUpdatePanel(final SvnVcs vcs) {
    myVCS = vcs;
  }

  protected void init(final Collection<FilePath> roots) {
    final JPanel configureRootsPanel = getRootsPanel();
    configureRootsPanel.setLayout(new BorderLayout());

    for (FilePath root : roots) {
      SVNURL url = getUrlFor(root);
      if (url != null) {
        myRootToPanel.put(root, createRootPanel(root, myVCS));
      }

      if (myRootToPanel.size() == 1) {

        configureRootsPanel.add(myRootToPanel.values().iterator().next().getPanel(), BorderLayout.CENTER);
      }
      else {
        final MultipleRootsEditor multipleRootsEditor = new MultipleRootsEditor(myRootToPanel, myVCS.getProject());
        configureRootsPanel.add(multipleRootsEditor.getPanel(), BorderLayout.CENTER);
        /*
        final JTabbedPane tabbedPane = new JTabbedPane();
        configureRootsPanel.add(tabbedPane, BorderLayout.CENTER);

        for (Iterator<FilePath> iterator = myRootToPanel.keySet().iterator(); iterator.hasNext();) {
          FilePath filePath = iterator.next();
          tabbedPane.add(myRootToPanel.get(filePath).getPanel(), filePath.getPath());
        }
        */

      }

    }
  }

  protected abstract SvnPanel createRootPanel(final FilePath root, final SvnVcs p1);

  protected abstract JPanel getRootsPanel();

  public void reset(final SvnConfiguration configuration) {
    getStatusBox().setSelected(configuration.UPDATE_RUN_STATUS);
    getRecursiveBox().setSelected(configuration.UPDATE_RECURSIVELY);

    for (FilePath filePath : myRootToPanel.keySet()) {
      myRootToPanel.get(filePath).reset(configuration);
    }

  }

  public void apply(final SvnConfiguration configuration) throws ConfigurationException {
    configuration.UPDATE_RUN_STATUS = getStatusBox().isSelected();
    configuration.UPDATE_RECURSIVELY = getRecursiveBox().isSelected();

    for (FilePath filePath : myRootToPanel.keySet()) {
      final SvnPanel svnPanel = myRootToPanel.get(filePath);
      if (svnPanel.canApply()) {
        svnPanel.apply(configuration);
      }
    }
  }

  private SVNURL getUrlFor(final FilePath root) {
    try {
      SVNWCClient wcClient = myVCS.createWCClient();
      return wcClient.doInfo(root.getIOFile(), SVNRevision.WORKING).getURL();
    }
    catch (SVNException e) {
      return null;
    }

  }

  protected abstract JComponent getPanel();

  protected abstract JCheckBox getStatusBox();

  protected abstract JCheckBox getRecursiveBox();
}

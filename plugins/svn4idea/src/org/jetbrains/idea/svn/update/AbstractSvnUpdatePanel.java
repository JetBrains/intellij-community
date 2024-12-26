// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.util.FilePathByPathComparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.DepthCombo;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.info.Info;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class AbstractSvnUpdatePanel {
  protected final SvnVcs myVCS;

  private final Map<FilePath, SvnPanel> myRootToPanel = new LinkedHashMap<>();

  public AbstractSvnUpdatePanel(final SvnVcs vcs) {
    myVCS = vcs;
  }

  protected void init(final Collection<FilePath> roots) {
    final JPanel configureRootsPanel = getRootsPanel();
    configureRootsPanel.setLayout(new BorderLayout());

    final ArrayList<FilePath> rootsCopy = new ArrayList<>(roots);
    rootsCopy.sort(FilePathByPathComparator.getInstance());
    for (FilePath root : rootsCopy) {
      Url url = getUrlFor(root);
      if (url != null) {
        myRootToPanel.put(root, createRootPanel(root, myVCS, roots));
      }

      Container parent = configureRootsPanel.getParent();
      parent.remove(configureRootsPanel);
      parent.setLayout(new BorderLayout());
      JPanel additionalPanel = getAdditionalPanel();
      if (additionalPanel != null) {
        parent.remove(additionalPanel);
      }
      if (myRootToPanel.size() == 1) {
        configureRootsPanel.add(myRootToPanel.values().iterator().next().getPanel(), BorderLayout.CENTER);
        parent.add(configureRootsPanel, BorderLayout.NORTH);
        if (additionalPanel != null) {
          parent.add(additionalPanel, BorderLayout.CENTER);
        }
      }
      else {
        final MultipleRootEditorWithSplitter multipleRootsEditor = new MultipleRootEditorWithSplitter(myRootToPanel, myVCS.getProject());
        configureRootsPanel.add(multipleRootsEditor, BorderLayout.CENTER);
        parent.add(configureRootsPanel, BorderLayout.CENTER);
        if (additionalPanel != null) {
          parent.add(additionalPanel, BorderLayout.SOUTH);
        }
      }
    }
  }

  protected abstract SvnPanel createRootPanel(final FilePath root, final SvnVcs p1, Collection<FilePath> roots);

  protected abstract JPanel getRootsPanel();

  protected JPanel getAdditionalPanel() {
    return null;
  }

  public void reset(final SvnConfiguration configuration) {
    getDepthBox().setSelectedItem(configuration.getUpdateDepth());

    for (FilePath filePath : myRootToPanel.keySet()) {
      myRootToPanel.get(filePath).reset(configuration);
    }

  }

  public void apply(final SvnConfiguration configuration) throws ConfigurationException {
    configuration.setUpdateDepth(getDepthBox().getDepth());

    for (FilePath filePath : myRootToPanel.keySet()) {
      final SvnPanel svnPanel = myRootToPanel.get(filePath);
      if (svnPanel.canApply()) {
        svnPanel.apply(configuration);
      }
    }
  }

  private @Nullable Url getUrlFor(final @NotNull FilePath root) {
    final Info info = myVCS.getInfo(root.getIOFile());
    return info != null ? info.getUrl() : null;
  }

  protected abstract JComponent getPanel();

  protected abstract DepthCombo getDepthBox();
}

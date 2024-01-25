// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.pom.Navigatable;
import icons.MavenIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

class RepositoryNode extends MavenSimpleNode {
  
  @NlsSafe
  private final String myId;
  @NlsSafe
  private final String myUrl;
  private final boolean myLocal;

  RepositoryNode(MavenProjectsStructure structure, RepositoriesNode parent, String id, String url, boolean local) {
    super(structure, parent);
    myId = id;
    myUrl = url;
    myLocal = local;
    getTemplatePresentation().setIcon(getDefaultIcon());
  }

  @NotNull
  private Icon getDefaultIcon() {
    return myLocal ? MavenIcons.MavenRepoLocal : MavenIcons.MavenRepoRemote;
  }

  @Override
  public String getName() {
    return myId;
  }

  @Override
  @NonNls
  protected String getMenuId() {
    return "Maven.RepositoryMenu";
  }

  String getId() {
    return myId;
  }

  String getUrl() {
    return myUrl;
  }

  boolean isLocal() {
    return myLocal;
  }

  @Nullable
  @Override
  public Navigatable getNavigatable() {
    return null;
  }
}

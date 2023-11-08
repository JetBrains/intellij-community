// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.pom.Navigatable;
import icons.MavenIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.ui.UiUtils.getPresentablePath;

class RepositoryNode extends MavenSimpleNode {

  private final String myId;
  private final String myUrl;
  private final boolean myLocal;

  RepositoryNode(MavenProjectsStructure structure, RepositoriesNode parent, String id, String url, boolean local) {
    super(structure, parent);
    myId = id;
    myUrl = url;
    myLocal = local;
    getTemplatePresentation().setIcon(myLocal ? MavenIcons.MavenRepoLocal : MavenIcons.MavenRepoRemote);
  }

  @Override
  public String getName() {
    return myId;
  }

  @Override
  @NonNls
  protected String getMenuId() {
    return null;
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

  @Override
  protected void doUpdate(@NotNull PresentationData presentation) {
    setNameAndTooltip(presentation, myId, null, myLocal ? getPresentablePath(myUrl) : myUrl);
  }
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.Navigatable;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.SimpleTextAttributes;
import icons.MavenIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.server.MavenIndexUpdateState;

import javax.swing.*;

import static com.intellij.openapi.ui.UiUtils.getPresentablePath;
import static org.jetbrains.idea.maven.server.MavenIndexUpdateState.State.FAILED;
import static org.jetbrains.idea.maven.server.MavenIndexUpdateState.State.INDEXING;

class RepositoryNode extends MavenSimpleNode {

  private static Icon RUNNING = new AnimatedIcon.Default();

  @NlsSafe
  private final String myId;
  @NlsSafe
  private final String myUrl;
  private final boolean myLocal;
  private MavenIndexUpdateState myState = null;

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

  @Override
  protected void doUpdate(@NotNull PresentationData presentation) {
    MavenIndexUpdateState state = myState;
    if (state == null) {
      setDefaultState(presentation);
    }
    else {
      presentation.clearText();
      presentation.addText(myId, getPlainAttributes());
      if (state.myState == INDEXING) {
        presentation.setIcon(RUNNING);
        @NlsSafe String info = state.myProgressInfo;
        if (!StringUtil.isEmptyOrSpaces(info)) {
          presentation.addText(" (" + info + ")", SimpleTextAttributes.GRAY_ATTRIBUTES); //NON-NLS
        }
      }
      else if (state.myState == FAILED) {
        presentation.setIcon(AllIcons.Nodes.ErrorMark);
        getTemplatePresentation().setTooltip(state.myError); //NON-NLS
      }
      else {
        setDefaultState(presentation);
      }
    }
  }

  private void setDefaultState(@NotNull PresentationData presentation) {
    presentation.setIcon(getDefaultIcon());
    setNameAndTooltip(presentation, myId, null, myLocal ? getPresentablePath(myUrl) : myUrl);
  }

  public void setLastStatus(@Nullable MavenIndexUpdateState state) {
    myState = state;
  }
}

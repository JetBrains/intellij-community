// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import icons.SvnIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.status.Status;
import org.jetbrains.idea.svn.status.StatusType;

import javax.swing.*;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.join;
import static java.util.Arrays.asList;
import static org.jetbrains.idea.svn.SvnBundle.message;

public enum ConflictState {
  none(false, false, false, null),
  tree(true, false, false, SvnIcons.Conflictc),
  text(false, true, false, SvnIcons.Conflictt),
  prop(false, false, true, SvnIcons.Conflictp),
  tree_text(true, true, false, SvnIcons.Conflictct), // ? -
  tree_prop(true, false, true, SvnIcons.Conflictcp), // now falls but marked
  text_prop(false, true, true, SvnIcons.Conflicttp),
  all3(true, true, true, SvnIcons.Conflictctp);       // ? -

  private final boolean myTree;
  private final boolean myText;
  private final boolean myProperty;
  private final @Nullable Icon myIcon;

  ConflictState(final boolean tree, final boolean text, final boolean property, @Nullable final Icon icon) {
    myTree = tree;
    myText = text;
    myProperty = property;

    myIcon = icon;
  }

  public boolean isTree() {
    return myTree;
  }

  public boolean isText() {
    return myText;
  }

  public boolean isProperty() {
    return myProperty;
  }

  public boolean isConflict() {
    return myProperty || myText || myTree;
  }

  @Nullable
  public Icon getIcon() {
    return myIcon;
  }

  @Nls
  public String getDescription() {
    if (!isConflict()) return null;

    List<String> conflicts = asList(
      myTree ? message("file.conflict.tree") : null,
      myText ? message("file.conflict.text") : null,
      myProperty ? message("file.conflict.property") : null
    );
    return join(conflicts, ", ");
  }

  public static ConflictState mergeState(final ConflictState leftState, final ConflictState rightState) {
    return getInstance(leftState.myTree | rightState.myTree, leftState.myText | rightState.myText,
                             leftState.myProperty | rightState.myProperty);
  }

  @NotNull
  public static ConflictState getInstance(final boolean tree, final boolean text, final boolean property) {
    final ConflictState[] conflictStates = values();
    for (ConflictState state : conflictStates) {
      if ((state.isTree() == tree) && (state.isText() == text) && (state.isProperty() == property)) {
        return state;
      }
    }
    // all combinations are defined
    assert false;
    return null;
  }

  @NotNull
  public static ConflictState from(@NotNull Status status) {
    return getInstance(status.getTreeConflict() != null, status.is(StatusType.STATUS_CONFLICTED),
                       status.isProperty(StatusType.STATUS_CONFLICTED));
  }
}

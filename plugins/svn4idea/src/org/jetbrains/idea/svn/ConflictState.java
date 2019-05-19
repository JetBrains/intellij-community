// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import icons.SvnIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.status.Status;
import org.jetbrains.idea.svn.status.StatusType;

import javax.swing.*;

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
  @Nullable
  private final Icon myIcon;
  private final String myDescription;

  ConflictState(final boolean tree, final boolean text, final boolean property, @Nullable final Icon icon) {
    myTree = tree;
    myText = text;
    myProperty = property;

    myIcon = icon;

    myDescription = createDescription();
  }

  @Nullable
  private String createDescription() {
    int cnt = 0;
    final StringBuilder sb = new StringBuilder();
    cnt = checkOne(myTree, cnt, sb, "tree");
    cnt = checkOne(myText, cnt, sb, "text");
    cnt = checkOne(myProperty, cnt, sb, "property");
    if (cnt == 0) {
      return null;
    }
    return sb.toString();
  }

  private static int checkOne(final boolean value, final int init, final StringBuilder sb, final String text) {
    if (value) {
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append(text);
      return init + 1;
    }
    return init;
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

  public String getDescription() {
    return myDescription;
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

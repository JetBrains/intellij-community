/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.ui.popup;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.project.Project;

public interface TreePopupStep<T> extends PopupStep<T> {

  AbstractTreeStructure getStructure();

  boolean isSelectable(T node, T userData);

  boolean isRootVisible();

  Project getProject();
}

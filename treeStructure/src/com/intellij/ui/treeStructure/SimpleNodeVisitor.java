/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.treeStructure;

/**
 * @author kir
 */
public interface SimpleNodeVisitor {
  /** return true if no further recursive processing is required */
  boolean accept(SimpleNode simpleNode);
}

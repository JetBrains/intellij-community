/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.templateLanguages;

import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.CharTable;

public interface TreePatcher {
  /** Inserts toInsert into destinationTree according to parser rules.*/
  void insert(CompositeElement parent, TreeElement anchorBefore, OuterLanguageElement toInsert);

  /** If leaf need to be split to insert OuterLanguageElement this function is called
   * @return first part of the split
   */
  LeafElement split(LeafElement leaf, int offset, final CharTable table);
}

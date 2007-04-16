/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.ide.structureView.impl.xml;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.Grouper;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public interface XmlStructureViewElementProvider {
  @NonNls String EXTENSION_POINT_NAME = "com.intellij.xmlStructureViewElementProvider";

  @Nullable
  StructureViewTreeElement createCustomXmlTagTreeElement(@NotNull XmlTag tag);

  Sorter[] getSorters(@NotNull final XmlFile file);

  Grouper[] getGroupers(@NotNull final XmlFile file);

  Filter[] getFilters(@NotNull final XmlFile file);
}

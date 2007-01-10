/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.ide.structureView.impl.xml;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.psi.xml.XmlTag;

/**
 * @author peter
 */
public interface XmlStructureViewElementProvider {
  @NonNls String EXTENSION_POINT_NAME = "com.intellij.xmlStructureViewElementProvider";

  @Nullable
  StructureViewTreeElement createCustomXmlTagTreeElement(@NotNull XmlTag tag);
}

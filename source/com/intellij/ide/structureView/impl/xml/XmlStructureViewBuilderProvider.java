package com.intellij.ide.structureView.impl.xml;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface XmlStructureViewBuilderProvider {
  @NonNls String EXTENSION_POINT_NAME = "com.intellij.xmlStructureViewBuilderProvider";

  @Nullable
  StructureViewBuilder createStructureViewBuilder(@NotNull XmlFile file);
}

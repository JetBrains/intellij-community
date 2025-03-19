// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.structure;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.xml.XmlStructureViewBuilderProvider;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Function;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomService;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public final class DomStructureViewBuilderProvider implements XmlStructureViewBuilderProvider {
  @Override
  public StructureViewBuilder createStructureViewBuilder(@NotNull XmlFile file) {
    if (DomManager.getDomManager(file.getProject()).getDomFileDescription(file) != null) {
      return new DomStructureViewBuilder(file, DESCRIPTOR);
    }
    return null;
  }

  public static final Function<DomElement,DomService.StructureViewMode> DESCRIPTOR = element -> DomService.StructureViewMode.SHOW;
}

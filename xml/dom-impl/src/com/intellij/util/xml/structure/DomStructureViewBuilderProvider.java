/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
 * This SHOULD NOT be subclassed!
 *
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

  public static final Function<DomElement,DomService.StructureViewMode> DESCRIPTOR =
    element -> DomService.StructureViewMode.SHOW;
}

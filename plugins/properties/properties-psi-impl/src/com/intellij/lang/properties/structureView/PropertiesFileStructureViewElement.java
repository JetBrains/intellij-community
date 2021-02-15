// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.editor.PropertyStructureViewElement;
import com.intellij.lang.properties.psi.impl.PropertiesFileImpl;
import com.intellij.navigation.ItemPresentation;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BooleanSupplier;

public class PropertiesFileStructureViewElement extends PsiTreeElementBase<PropertiesFileImpl> {
  private final BooleanSupplier myGrouped;

  protected PropertiesFileStructureViewElement(PropertiesFileImpl propertiesFile, BooleanSupplier grouped) {
    super(propertiesFile);
    myGrouped = grouped;
  }

  @Override
  @NotNull
  public Collection<StructureViewTreeElement> getChildrenBase() {
    List<? extends IProperty> properties = getElement().getProperties();

    Collection<StructureViewTreeElement> elements = new ArrayList<>(properties.size());
    for (IProperty property : properties) {
      elements.add(new PropertyStructureViewElement(property, myGrouped));
    }
    return elements;
  }

  @Override
  public String getPresentableText() {
    return getElement().getName();
  }

  @Override
  @NotNull
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      @Override
      public String getPresentableText() {
        return PropertiesFileStructureViewElement.this.getPresentableText();
      }

      @Override
      public Icon getIcon(boolean open) {
        return getElement().getIcon(0);
      }
    };
  }
}

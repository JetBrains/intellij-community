/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.lang.properties.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.editor.ResourceBundleEditorViewElement;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.impl.PropertiesFileImpl;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author max
 */
public class PropertiesFileStructureViewElement extends PsiTreeElementBase<PropertiesFileImpl> implements ResourceBundleEditorViewElement {

  protected PropertiesFileStructureViewElement(PropertiesFileImpl propertiesFile) {
    super(propertiesFile);
  }

  @NotNull
  public Collection<StructureViewTreeElement> getChildrenBase() {
    List<? extends IProperty> properties = getElement().getProperties();

    Collection<StructureViewTreeElement> elements = new ArrayList<>(properties.size());
    for (IProperty property : properties) {
      elements.add(new PropertiesStructureViewElement((Property)property));
    }
    return elements;
  }

  @Nullable
  @Override
  public IProperty[] getProperties() {
    return null;
  }

  @NotNull
  @Override
  public PsiFile[] getFiles() {
    return new PsiFile[] {getValue()};
  }

  public String getPresentableText() {
    return getElement().getName();
  }

  @NotNull
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      public String getPresentableText() {
        return PropertiesFileStructureViewElement.this.getPresentableText();
      }

      public String getLocationString() {
        return null;
      }

      public Icon getIcon(boolean open) {
        return getElement().getIcon(0);
      }
    };
  }
}

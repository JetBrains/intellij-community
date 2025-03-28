// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.impl.xml;

import com.intellij.ide.structureView.StructureViewExtension;
import com.intellij.ide.structureView.StructureViewFactoryEx;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.lang.dtd.DTDLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XmlStructureViewTreeModel extends TextEditorBasedStructureViewModel {
  private static final Class[] CLASSES =
    {XmlTag.class, XmlFile.class, XmlEntityDecl.class, XmlElementDecl.class, XmlAttlistDecl.class, XmlConditionalSection.class};
  private static final Sorter[] SORTERS = {Sorter.ALPHA_SORTER};

  public XmlStructureViewTreeModel(@NotNull XmlFile file, @Nullable Editor editor) {
    super(editor, file);
  }

  @Override
  public @NotNull StructureViewTreeElement getRoot() {
    XmlFile myFile = getPsiFile();
    if (myFile.getLanguage() == DTDLanguage.INSTANCE) return new DtdFileTreeElement(myFile);
    return new XmlFileTreeElement(myFile);
  }

  @Override
  public boolean shouldEnterElement(final Object element) {
    return element instanceof XmlTag && ((XmlTag)element).getSubTags().length > 0;
  }

  @Override
  protected XmlFile getPsiFile() {
    return (XmlFile)super.getPsiFile();
  }

  @Override
  protected Class @NotNull [] getSuitableClasses() {
    return CLASSES;
  }

  @Override
  public Object getCurrentEditorElement() {
    final Object editorElement = super.getCurrentEditorElement();
    if (editorElement instanceof XmlTag) {
      PsiUtilCore.ensureValid((XmlTag)editorElement);
      for (StructureViewExtension extension : StructureViewFactoryEx.getInstanceEx(getPsiFile().getProject()).getAllExtensions(XmlTag.class)) {
        final Object element = extension.getCurrentEditorElement(getEditor(), (PsiElement)editorElement);
        if (element != null) return element;
      }
    }
    return editorElement;
  }

  @Override
  public Sorter @NotNull [] getSorters() {
    return SORTERS;
  }
}
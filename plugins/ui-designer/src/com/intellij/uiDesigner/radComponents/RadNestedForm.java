// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.ResourceFileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiUtil;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.CompiledClassPropertiesProvider;
import com.intellij.uiDesigner.lw.LwRootContainer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;


public class RadNestedForm extends RadComponent {
  private static final Logger LOG = Logger.getInstance(RadNestedForm.class);

  private final String myFormFileName;
  private final RadRootContainer myRootContainer;

  public RadNestedForm(final ModuleProvider module, final String formFileName, final String id) throws Exception {
    super(module, JPanel.class, id);
    myFormFileName = formFileName;
    LOG.debug("Loading nested form " + formFileName);
    VirtualFile formFile = ResourceFileUtil.findResourceFileInDependents(getModule(), formFileName);
    if (formFile == null) {
      throw new IllegalArgumentException("Couldn't find virtual file for nested form " + formFileName);
    }
    Document doc = FileDocumentManager.getInstance().getDocument(formFile);
    final ClassLoader classLoader = LoaderFactory.getInstance(getProject()).getLoader(formFile);
    final LwRootContainer rootContainer = Utils.getRootContainer(doc.getText(), new CompiledClassPropertiesProvider(classLoader));
    myRootContainer = XmlReader.createRoot(module, rootContainer, classLoader, null);
    if (myRootContainer.getComponentCount() > 0) {
      getDelegee().setLayout(new BorderLayout());
      JComponent nestedFormDelegee = myRootContainer.getComponent(0).getDelegee();
      getDelegee().add(nestedFormDelegee, BorderLayout.CENTER);

      setRadComponentRecursive(nestedFormDelegee);
    }

    if (isCustomCreateRequired()) {
      setCustomCreate(true);
    }
  }

  private void setRadComponentRecursive(final JComponent component) {
    component.putClientProperty(CLIENT_PROP_RAD_COMPONENT, this);
    for (int i = 0; i < component.getComponentCount(); i++) {
      final Component child = component.getComponent(i);
      if (child instanceof JComponent) {
        setRadComponentRecursive((JComponent)child);
      }
    }
  }

  @Override
  public void write(XmlWriter writer) {
    writer.startElement(UIFormXmlConstants.ELEMENT_NESTED_FORM);
    try {
      writeId(writer);
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_FORM_FILE, myFormFileName);
      writeBinding(writer);
      writeConstraints(writer);
    }
    finally {
      writer.endElement(); // component
    }
  }

  @Override
  public @NotNull String getComponentClassName() {
    return myRootContainer.getClassToBind();
  }

  @Override
  public boolean hasIntrospectedProperties() {
    return false;
  }

  @Override
  public boolean isCustomCreateRequired() {
    if (super.isCustomCreateRequired()) return true;
    PsiClass boundClass = FormEditingUtil.findClassToBind(getModule(), myRootContainer.getClassToBind());
    return isNonStaticInnerClass(boundClass);
  }

  private static boolean isNonStaticInnerClass(final PsiClass boundClass) {
    if (boundClass == null) return false;
    return PsiUtil.isInnerClass(boundClass) || isNonStaticInnerClass(boundClass.getContainingClass());
  }
}

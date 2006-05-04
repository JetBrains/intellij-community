/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.uiDesigner.LoaderFactory;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlReader;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.CompiledClassPropertiesProvider;
import com.intellij.uiDesigner.lw.LwRootContainer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Component;

/**
 * @author yole
 */
public class RadNestedForm extends RadComponent {
  private String myFormFileName;
  private RadRootContainer myRootContainer;

  public RadNestedForm(final Module module, final String formFileName, final String id) throws Exception {
    super(module, JPanel.class, id);
    myFormFileName = formFileName;
    VirtualFile formFile = ModuleUtil.findResourceFile(formFileName, module);
    final ClassLoader classLoader = LoaderFactory.getInstance(module.getProject()).getLoader(formFile);

    final LwRootContainer rootContainer = Utils.getRootContainer(formFile.getInputStream(), new CompiledClassPropertiesProvider(classLoader));
    myRootContainer = XmlReader.createRoot(module, rootContainer, classLoader);
    if (myRootContainer.getComponentCount() > 0) {
      getDelegee().setLayout(new BorderLayout());
      JComponent nestedFormDelegee = myRootContainer.getComponent(0).getDelegee();
      getDelegee().add(nestedFormDelegee, BorderLayout.CENTER);

      setRadComponentRecursive(nestedFormDelegee);
    }
  }

  private void setRadComponentRecursive(final JComponent component) {
    component.putClientProperty(CLIENT_PROP_RAD_COMPONENT, this);
    for(int i=0; i<component.getComponentCount(); i++) {
      final Component child = component.getComponent(i);
      if (child instanceof JComponent) {
        setRadComponentRecursive((JComponent) child);
      }
    }
  }

  public void write(XmlWriter writer) {
    writer.startElement(UIFormXmlConstants.ELEMENT_NESTED_FORM);
    try{
      writeId(writer);
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_FORM_FILE, myFormFileName);
      writeBinding(writer);
      writeConstraints(writer);
    } finally {
      writer.endElement(); // component
    }
  }

  @Override @NotNull
  public String getComponentClassName() {
    return myRootContainer.getClassToBind();
  }

  @Override public boolean hasIntrospectedProperties() {
    return false;
  }
}

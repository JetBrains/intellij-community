// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.ModuleProvider;
import com.intellij.uiDesigner.XmlWriter;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public final class RadErrorComponent extends RadAtomicComponent {

  private final String myComponentClassName;
  private final Element myProperties;
  private final @Nls String myErrorDescription;

  public static RadErrorComponent create(
    final ModuleProvider module,
    final String id,
    final String componentClassName,
    final Element properties,
    @NotNull final @Nls String errorDescription
  ) {
    return new RadErrorComponent(module, id, componentClassName, properties, errorDescription);
  }

  private RadErrorComponent(
    final ModuleProvider module,
    final String id,
    @NotNull final String componentClassName,
    @Nullable final Element properties,
    @NotNull final @Nls String errorDescription
  ) {
    super(module, MyComponent.class, id);

    myComponentClassName = componentClassName;
    myErrorDescription = errorDescription;
    myProperties = properties;
  }

  @Override
  @NotNull
  public String getComponentClassName() {
    return myComponentClassName;
  }

  public @Nls String getErrorDescription() {
    return myErrorDescription;
  }

  @Override
  public void write(final XmlWriter writer) {
    writer.startElement("component");
    try {
      writeId(writer);

      // write class
      writer.addAttribute("class", myComponentClassName);

      writeBinding(writer);
      writeConstraints(writer);

      // write properties (if any)
      if (myProperties != null) {
        writer.writeElement(myProperties);
      }
    }
    finally {
      writer.endElement(); // component
    }
  }

  private static final class MyComponent extends JComponent {
    MyComponent() {
      setMinimumSize(new Dimension(20, 20));
    }

    @Override
    public void paint(final Graphics g) {
      g.setColor(Color.red);
      g.fillRect(0, 0, getWidth(), getHeight());
    }
  }

  @Override
  public boolean hasIntrospectedProperties() {
    return false;
  }
}

/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.ModuleProvider;
import com.intellij.uiDesigner.XmlWriter;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class RadErrorComponent extends RadAtomicComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.RadErrorComponent");

  private final String myComponentClassName;
  private final Element myProperties;
  private final String myErrorDescription;

  public static RadErrorComponent create(
    final ModuleProvider module,
    final String id,
    final String componentClassName,
    final Element properties,
    @NotNull final String errorDescription
  ) {
    return new RadErrorComponent(module, id, componentClassName, properties, errorDescription);
  }

  private RadErrorComponent(
    final ModuleProvider module,
    final String id,
    @NotNull final String componentClassName,
    @Nullable final Element properties,
    @NotNull final String errorDescription
  ) {
    super(module, MyComponent.class, id);

    myComponentClassName = componentClassName;
    myErrorDescription = errorDescription;
    myProperties = properties;
  }

  @NotNull
  public String getComponentClassName() {
    return myComponentClassName;
  }

  public String getErrorDescription() {
    return myErrorDescription;
  }

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
    public MyComponent() {
      setMinimumSize(new Dimension(20, 20));
    }

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

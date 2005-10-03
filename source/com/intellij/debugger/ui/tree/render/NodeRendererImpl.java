/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.tree.render;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

/**
 * @author Eugene Zhuravlev
 *         Date: Feb 9, 2005
 */
public abstract class NodeRendererImpl implements NodeRenderer{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.tree.render.NodeRendererImpl");
  protected BasicRendererProperties myProperties = new BasicRendererProperties();

  protected NodeRendererImpl() {
    //noinspection HardCodedStringLiteral
    myProperties.setName("unnamed");
  }

  public String getName() {
    return myProperties.getName();
  }

  public void setName(String name) {
    myProperties.setName(name);
  }

  public boolean isEnabled() {
    return myProperties.isEnabled();
  }

  public void setEnabled(boolean enabled) {
    myProperties.setEnabled(enabled);
  }

  public NodeRendererImpl clone() {
    try {
      final NodeRendererImpl cloned = (NodeRendererImpl)super.clone();
      cloned.myProperties = myProperties.clone();
      return cloned;
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e);
    }
    return null;
  }

  public void readExternal(Element element) throws InvalidDataException {
    myProperties.readExternal(element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    myProperties.writeExternal(element);
  }

  public String toString() {
    return getName();
  }
}

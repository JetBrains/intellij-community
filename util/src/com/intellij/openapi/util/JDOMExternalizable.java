/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util;

import org.jdom.Element;

/**
 *
 *
 */
public interface JDOMExternalizable {
  void readExternal(Element element) throws InvalidDataException;
  void writeExternal(Element element) throws WriteExternalException;
}

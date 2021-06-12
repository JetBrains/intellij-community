// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.javaee;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * Maps XML-namespace to bundled resource.
 *
 * @author Dmitry Avdeev
 */
public final class StandardResourceEP {
  public static final ExtensionPointName<StandardResourceEP> EP_NAME = new ExtensionPointName<>("com.intellij.standardResource");

  /**
   * URL or URI to be mapped to given resource, e.g. {@code http://www.w3.org/2001/XMLSchema}.
   */
  @Attribute("url")
  @RequiredElement
  public String url;

  /**
   * Path to resource, e.g. {@code foo/bar.xsd} (without leading slash).
   */
  @Attribute("path")
  @RequiredElement
  public String resourcePath;

  @Attribute("version")
  public String version;
}

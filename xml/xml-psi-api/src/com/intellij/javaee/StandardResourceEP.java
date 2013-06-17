/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.javaee;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author Dmitry Avdeev
 */
public class StandardResourceEP extends AbstractExtensionPointBean {

  public static final ExtensionPointName<StandardResourceEP> EP_NAME = ExtensionPointName.create("com.intellij.standardResource");

  /**
   * URL or URI to be mapped to given resource, e.g. http://www.w3.org/2001/XMLSchema
   */
  @Attribute("url")
  public String url;

  /**
   * Path to resource, e.g. foo/bar.xsd (without leading slash)
   */
  @Attribute("path")
  public String resourcePath;

  @Attribute("version")
  public String version;
}

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.maven.model.impl;

import com.intellij.util.xmlb.annotations.XCollection;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ibessonov
 */
public class MavenAnnotationProcessorsModel {

  public static final String COMPONENT_NAME = "MavenAnnotationProcessors";

  @XCollection(propertyElementName = "modules", elementName = "module", valueAttributeName = "name")
  public List<String> annotationProcessorModules = new ArrayList<>();
}
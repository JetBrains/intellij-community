/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.jps.maven.model.impl;

import com.intellij.util.xmlb.annotations.XCollection;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Sergey Evdokimov
 */
public class FilePattern {
  @XCollection(elementName = "pattern", style = XCollection.Style.v2)
  public Set<String> includes = new HashSet<>();

  @XCollection(elementName = "pattern", style = XCollection.Style.v2)
  public Set<String> excludes = new HashSet<>();
}

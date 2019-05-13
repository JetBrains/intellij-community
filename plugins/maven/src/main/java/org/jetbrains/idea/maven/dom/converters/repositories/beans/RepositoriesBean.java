/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.idea.maven.dom.converters.repositories.beans;

import com.intellij.util.xmlb.annotations.XCollection;

public class RepositoriesBean {
  @XCollection(propertyElementName = "repositories")
  public RepositoryBeanInfo[] myRepositories;

  public RepositoryBeanInfo[] getRepositories() {
    return myRepositories;
  }
}
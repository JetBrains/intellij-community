package com.intellij.openapi.components.impl;

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;

public class ComponentManagerConfig {
  @Tag(name = "application-components")
  @AbstractCollection(surroundWithTag = false)
  public ComponentConfig[] applicationComponents;

  @Tag(name = "project-components")
  @AbstractCollection(surroundWithTag = false)
  public ComponentConfig[] projectComponents;

  @Tag(name = "module-components")
  @AbstractCollection(surroundWithTag = false)
  public ComponentConfig[] moduleComponents;
}

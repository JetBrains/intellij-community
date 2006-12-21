package com.intellij.openapi.components.impl;

import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "component")
public class ComponentConfig {
  @Tag(name = "implementation-class")
  public String implementationClass;

  @Tag(name = "interface-class")
  public String interfaceClass;

  @Tag(name = "headless-implementation-class")
  public String headlessImplementationClass;

  //todo: empty tag means TRUE
  @Tag(name = "skipForDummyProject")
  public boolean skipForDummyProject;

  @Property(surroundWithTag = false)
  @MapAnnotation(surroundWithTag = false, entryTagName = "option", keyAttributeName = "name", valueAttributeName = "value")
  public Map<String,String> options = new HashMap<String, String>();
}

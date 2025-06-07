// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.maven.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.maven.model.JpsMavenModuleExtension;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;

import java.util.ArrayList;
import java.util.List;

public class JpsMavenModuleExtensionImpl extends JpsElementBase<JpsMavenModuleExtensionImpl> implements JpsMavenModuleExtension {
  public static final JpsElementChildRole<JpsMavenModuleExtension> ROLE = JpsElementChildRoleBase.create("maven");

  private final List<String> myAnnotationProcessorModules = new ArrayList<>();

  @Override
  public List<String> getAnnotationProcessorModules() {
    return myAnnotationProcessorModules;
  }

  @Override
  public @NotNull JpsMavenModuleExtensionImpl createCopy() {
    JpsMavenModuleExtensionImpl extension = new JpsMavenModuleExtensionImpl();
    extension.myAnnotationProcessorModules.addAll(this.myAnnotationProcessorModules);
    return extension;
  }
}

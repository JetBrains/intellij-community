package org.jetbrains.jps.maven.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.maven.model.JpsMavenModuleExtension;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;

/**
 * @author nik
 */
public class JpsMavenModuleExtensionImpl extends JpsElementBase<JpsMavenModuleExtensionImpl> implements JpsMavenModuleExtension {
  public static final JpsElementChildRole<JpsMavenModuleExtension> ROLE = JpsElementChildRoleBase.create("maven");

  public JpsMavenModuleExtensionImpl() {
  }

  @NotNull
  @Override
  public JpsMavenModuleExtensionImpl createCopy() {
    return new JpsMavenModuleExtensionImpl();
  }

  @Override
  public void applyChanges(@NotNull JpsMavenModuleExtensionImpl modified) {
  }
}

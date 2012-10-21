package org.jetbrains.jps.maven.model.impl;

import com.intellij.util.xmlb.XmlSerializerUtil;
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

  private MavenModuleExtensionProperties myState = new MavenModuleExtensionProperties();

  public JpsMavenModuleExtensionImpl() {
  }

  @NotNull
  @Override
  public MavenModuleExtensionProperties getState() {
    return myState;
  }

  @Override
  public void setState(MavenModuleExtensionProperties state) {
    myState = state;
  }

  @NotNull
  @Override
  public JpsMavenModuleExtensionImpl createCopy() {
    final JpsMavenModuleExtensionImpl copy = new JpsMavenModuleExtensionImpl();
    XmlSerializerUtil.copyBean(myState, copy.myState);
    return copy;
  }

  @Override
  public void applyChanges(@NotNull JpsMavenModuleExtensionImpl modified) {
    XmlSerializerUtil.copyBean(modified.myState, myState);
  }
}

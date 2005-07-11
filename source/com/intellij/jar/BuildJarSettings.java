package com.intellij.jar;

import com.intellij.j2ee.module.ModuleContainer;
import com.intellij.j2ee.module.ModuleContainerImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.JDOMExternalizer;
import org.jdom.Element;

/**
 * @author cdr
 */
public class BuildJarSettings implements ModuleComponent, JDOMExternalizable {
  private final ModuleContainer myModuleContainer;
  private String myJarPath = "";
  private boolean myBuildJar;
  private String myMainClass = "";

  public static BuildJarSettings getInstance(Module module) {
    return module.getComponent(BuildJarSettings.class);
  }
  public BuildJarSettings(Module module) {
    myModuleContainer = new ModuleContainerImpl(module);
  }

  public boolean isBuildJar() {
    return myBuildJar;
  }

  public String getMainClass() {
    return myMainClass;
  }

  public void readExternal(Element element) throws InvalidDataException {
    Element settings = element.getChild("containerInfo");
    if (settings != null) {
      myModuleContainer.readExternal(settings);
    }
    myJarPath = JDOMExternalizer.readString(element, "jarPath");
    myBuildJar = JDOMExternalizer.readBoolean(element, "buildJar");
    myMainClass = JDOMExternalizer.readString(element, "mainClass");
  }

  public void writeExternal(Element element) throws WriteExternalException {
    if (!myBuildJar) throw new WriteExternalException();
    Element settings = new Element("containerInfo");
    element.addContent(settings);
    myModuleContainer.writeExternal(settings);
    JDOMExternalizer.write(element, "jarPath", myJarPath);
    JDOMExternalizer.write(element, "buildJar", myBuildJar);
    JDOMExternalizer.write(element, "mainClass", myMainClass);
  }

  public ModuleContainer getModuleContainer() {
    return myModuleContainer;
  }

  public String getJarPath() {
    return myJarPath;
  }

  public void setJarPath(final String jarPath) {
    myJarPath = jarPath;
  }
  public void projectOpened() {

  }

  public void projectClosed() {

  }

  public void moduleAdded() {

  }

  public String getComponentName() {
    return "BuildJarSettings";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }

  public void setBuildJar(final boolean buildJar) {
    myBuildJar = buildJar;
  }

  public void setMainClass(final String mainClass) {
    myMainClass = mainClass;
  }
}

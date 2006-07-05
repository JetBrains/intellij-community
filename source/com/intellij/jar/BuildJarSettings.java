package com.intellij.jar;

import com.intellij.javaee.make.MakeUtil;
import com.intellij.javaee.module.ModuleContainer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.JDOMExternalizer;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.execution.ExecutionBundle;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ide.IdeBundle;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class BuildJarSettings implements ModuleComponent, JDOMExternalizable {
  @NonNls private static final String ELEMENT_CONTAINERINFO = "containerInfo";
  private final ModuleContainer myModuleContainer;
  private final Module myModule;
  private String myJarUrl = "";
  private boolean myBuildJar;
  private String myMainClass = "";

  public static BuildJarSettings getInstance(Module module) {
    return module.getComponent(BuildJarSettings.class);
  }
  public BuildJarSettings(Module module) {
    myModule = module;
    myModuleContainer = MakeUtil.getInstance().createModuleContainer(myModule);
  }

  public boolean isBuildJar() {
    return myBuildJar;
  }

  public String getMainClass() {
    return myMainClass;
  }

  public void readExternal(Element element) throws InvalidDataException {
    Element settings = element.getChild(ELEMENT_CONTAINERINFO);
    if (settings != null) {
      myModuleContainer.readExternal(settings);
    }
    myJarUrl = JDOMExternalizer.readString(element, "jarUrl");
    myBuildJar = JDOMExternalizer.readBoolean(element, "buildJar");
    myMainClass = JDOMExternalizer.readString(element, "mainClass");
  }

  public void writeExternal(Element element) throws WriteExternalException {
    if (!myBuildJar) throw new WriteExternalException();
    Element settings = new Element(ELEMENT_CONTAINERINFO);
    element.addContent(settings);
    myModuleContainer.writeExternal(settings);
    JDOMExternalizer.write(element, "jarUrl", myJarUrl);
    JDOMExternalizer.write(element, "buildJar", myBuildJar);
    JDOMExternalizer.write(element, "mainClass", myMainClass);
  }

  public ModuleContainer getModuleContainer() {
    return myModuleContainer;
  }

  public String getJarUrl() {
    return myJarUrl;
  }

  public void setJarUrl(final String jarUrl) {
    myJarUrl = jarUrl;
  }
  public void projectOpened() {

  }

  public void projectClosed() {

  }

  public void moduleAdded() {

  }

  @NonNls @NotNull
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

  public void checkSettings() throws RuntimeConfigurationException {
    if (myMainClass != null && myMainClass.length() > 0) {
      final PsiManager psiManager = PsiManager.getInstance(myModule.getProject());
      final PsiClass aClass = psiManager.findClass(myMainClass, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule));
      if (aClass == null) {
        throw new RuntimeConfigurationError(IdeBundle.message("jar.build.class.not.found", myMainClass));
      }

      if (!ApplicationConfigurationType.hasMainMethod(aClass)) {
        throw new RuntimeConfigurationError(ExecutionBundle.message("main.method.not.found.in.class.error.message", myMainClass));
      }
    }
  }
}

package org.jetbrains.jps.maven.model.impl;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.maven.model.JpsMavenExtensionService;
import org.jetbrains.jps.maven.model.JpsMavenModuleExtension;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JDomSerializationUtil;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;

import static com.intellij.util.xmlb.XmlSerializer.deserialize;

/**
 * @author nik
 */
public class JpsMavenModelSerializationExtension extends JpsModelSerializerExtension {
  private static final String PRODUCTION_ON_TEST_ATTRIBUTE = "production-on-test";
  private static final String MAVEN_MODULE_ATTRIBUTE = "org.jetbrains.idea.maven.project.MavenProjectsManager.isMavenModule";
  private static final String MAVEN_SYSTEM_ID = "Maven";

  @Override
  public void loadModuleOptions(@NotNull JpsModule module, @NotNull Element rootElement) {
    boolean isMavenModule = Boolean.parseBoolean(rootElement.getAttributeValue(MAVEN_MODULE_ATTRIBUTE)) ||
                            rootElement.getChildren().stream()
                                       .anyMatch(element -> MAVEN_SYSTEM_ID.equals(element.getAttributeValue("externalSystem")));
    if (isMavenModule) {
      JpsMavenModuleExtension extension = JpsMavenExtensionService.getInstance().getOrCreateExtension(module);
      Element component = JDomSerializationUtil.findComponent(rootElement, MavenAnnotationProcessorsModel.COMPONENT_NAME);
      if (component != null) {
        MavenAnnotationProcessorsModel annotationProcessorsModel = deserialize(component, MavenAnnotationProcessorsModel.class);
        extension.getAnnotationProcessorModules().addAll(annotationProcessorsModel.annotationProcessorModules);
      }
    }
  }

  @Override
  public void saveModuleOptions(@NotNull JpsModule module, @NotNull Element rootElement) {
    if (JpsMavenExtensionService.getInstance().getExtension(module) != null) {
      rootElement.setAttribute(MAVEN_MODULE_ATTRIBUTE, "true");
    }
  }

  @Override
  public void loadModuleDependencyProperties(JpsDependencyElement dependency, Element orderEntry) {
    if (orderEntry.getAttributeValue(PRODUCTION_ON_TEST_ATTRIBUTE) != null) {
      JpsMavenExtensionService.getInstance().setProductionOnTestDependency(dependency, true);
    }
  }

  @Override
  public void saveModuleDependencyProperties(JpsDependencyElement dependency, Element orderEntry) {
    if (JpsMavenExtensionService.getInstance().isProductionOnTestDependency(dependency)) {
      orderEntry.setAttribute(PRODUCTION_ON_TEST_ATTRIBUTE, "");
    }
  }
}

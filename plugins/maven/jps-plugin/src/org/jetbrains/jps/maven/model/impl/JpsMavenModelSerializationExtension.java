package org.jetbrains.jps.maven.model.impl;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.maven.model.JpsMavenExtensionService;
import org.jetbrains.jps.maven.model.JpsMavenModuleExtension;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.facet.JpsFacetConfigurationSerializer;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class JpsMavenModelSerializationExtension extends JpsModelSerializerExtension {
  private static final String PRODUCTION_ON_TEST_ATTRIBUTE = "production-on-test";
  private static final String MAVEN_MODULE_ATTRIBUTE = "org.jetbrains.idea.maven.project.MavenProjectsManager.isMavenModule";

  @Override
  public void loadModuleOptions(@NotNull JpsModule module, @NotNull Element rootElement) {
    if (Boolean.parseBoolean(rootElement.getAttributeValue(MAVEN_MODULE_ATTRIBUTE))) {
      JpsMavenExtensionService.getInstance().getOrCreateExtension(module);
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


  private final JpsFacetConfigurationSerializer<JpsMavenModuleExtension> FACET_SERIALIZER = new JpsFacetConfigurationSerializer<JpsMavenModuleExtension>(JpsMavenModuleExtensionImpl.ROLE, "_maven_", "maven") {
    @Override
    public JpsMavenModuleExtension loadExtension(Element configurationElement,
                                                 String facetName,
                                                 JpsModule module,
                                                 String baseModulePath,
                                                 JpsElement parentFacet) {
      final JpsMavenModuleExtension extension = JpsMavenExtensionService.getInstance().getExtension(module);
      if (extension != null) {
        final MavenModuleExtensionProperties state = XmlSerializer.deserialize(configurationElement, MavenModuleExtensionProperties.class);
        if (state != null) {
          extension.setState(state);
        }
      }
      return extension;
    }

    @Override
    protected JpsMavenModuleExtension loadExtension(@NotNull Element facetConfigurationElement, String name, String baseModulePath, JpsElement parent, JpsModule module) {
      throw new RuntimeException("Not implemented");
    }

    @Override
    protected void saveExtension(JpsMavenModuleExtension extension, Element facetConfigurationTag, JpsModule module) {
      final MavenModuleExtensionProperties state = extension.getState();
      XmlSerializer.serializeInto(state, facetConfigurationTag);
    }
  };

  @Override
  public List<? extends JpsFacetConfigurationSerializer<?>> getFacetConfigurationSerializers() {
    return Collections.singletonList(FACET_SERIALIZER);
  }

}

package org.jetbrains.jps.maven.model;

import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsProjectData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JpsMavenModuleSerializationTest {
  @Test
  public void testLoadProject() {
    JpsProjectData projectData = JpsProjectData.loadFromTestData("plugins/maven/jps-plugin/testData/compiler/classpathTest", getClass());
    List<JpsModule> modules = projectData.getProject().getModules();
    assertEquals(3, modules.size());
    JpsModule dep = modules.get(0);
    assertEquals("dep", dep.getName());
    JpsModule depTest = modules.get(1);
    assertEquals("dep-test", depTest.getName());
    JpsModule main = modules.get(2);
    assertEquals("main", main.getName());

    for (JpsModule module : modules) {
      assertNotNull(getService().getExtension(module));
    }
    List<JpsDependencyElement> dependencies = main.getDependenciesList().getDependencies();
    assertEquals(5, dependencies.size());
    assertTrue(getService().isProductionOnTestDependency(dependencies.get(3)));
    assertFalse(getService().isProductionOnTestDependency(dependencies.get(4)));
  }

  private static JpsMavenExtensionService getService() {
    return JpsMavenExtensionService.getInstance();
  }
}

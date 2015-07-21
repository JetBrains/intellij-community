package org.jetbrains.jps.maven.model;

import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsSerializationTestCase;

import java.util.List;

/**
 * @author nik
 */
public class JpsMavenModuleSerializationTest extends JpsSerializationTestCase {
  public void testLoadProject() {
    loadProject("plugins/maven/jps-plugin/testData/compiler/classpathTest");
    List<JpsModule> modules = myProject.getModules();
    assertEquals(3, modules.size());
    JpsModule main = modules.get(0);
    assertEquals("main", main.getName());
    JpsModule dep = modules.get(1);
    assertEquals("dep", dep.getName());
    JpsModule depTest = modules.get(2);
    assertEquals("dep-test", depTest.getName());

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

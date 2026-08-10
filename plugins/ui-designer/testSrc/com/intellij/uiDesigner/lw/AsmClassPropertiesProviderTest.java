// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.lw;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.uiDesigner.compiler.Utils;
import com.sun.tools.javac.Main;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IDEA-392515: reading a form file must never execute code from the component classes it names.
 * {@link CompiledClassPropertiesProvider} used to do exactly that through
 * {@code Introspector.getBeanInfo()}, which loads and instantiates {@code <Component>BeanInfo}.
 */
public class AsmClassPropertiesProviderTest extends UsefulTestCase {
  private static final String MARKER_PROPERTY = "uiDesigner.test.payloadMarker";

  private Path myOutputDir;
  private Path myMarkerFile;
  private InstrumentationClassFinder myClassFinder;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myOutputDir = FileUtil.createTempDirectory("uiDesignerProviderTest", "").toPath();
    myMarkerFile = myOutputDir.resolve("payload-marker.txt");
    System.setProperty(MARKER_PROPERTY, myMarkerFile.toString());

    String testDataPath = PluginPathManager.getPluginHomePath("ui-designer") + "/testData/";
    List<String> sources = List.of("beanInfoSafety/Payload.java", "beanInfoSafety/HostileComponent.java",
                                   "beanInfoSafety/HostileComponentBeanInfo.java", "beanInfoSafety/NonPublicAccessorsComponent.java",
                                   "nonBaseModuleType/TimestampComponent.java",
                                   "enumProperty/Alignment.java", "enumProperty/EnumPropertyComponent.java");
    String[] args = new String[sources.size() + 2];
    args[0] = "-d";
    args[1] = myOutputDir.toString();
    for (int i = 0; i < sources.size(); i++) {
      args[i + 2] = testDataPath + sources.get(i);
    }
    assertEquals("testData sources failed to compile", 0, Main.compile(args));

    URL platformUrl = InstrumentationClassFinder.createJDKPlatformUrl(System.getProperty("java.home"));
    myClassFinder = new InstrumentationClassFinder(new URL[]{platformUrl}, new URL[]{outputDirUrl()});
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      System.clearProperty(MARKER_PROPERTY);
      if (myClassFinder != null) {
        myClassFinder.releaseResources();
        myClassFinder = null;
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testReadingFormDoesNotExecuteComponentCode() throws Exception {
    LwRootContainer rootContainer = readHostileForm(new AsmClassPropertiesProvider(myClassFinder));

    assertNoPayloadExecuted();

    LwContainer grid = (LwContainer)rootContainer.getComponent(0);
    LwComponent component = (LwComponent)grid.getComponent(0);
    assertEquals("HostileComponent", component.getComponentClassName());
    assertThat(component.getAssignedIntrospectedProperties()).extracting(LwIntrospectedProperty::getName)
      .containsExactly("label");
  }

  /**
   * IDEA-389274: Preview reads the form through the class loader of the {@link InstrumentationClassFinder}, which
   * cannot resolve types from non-base JDK modules - it would have to define {@code java.sql.Timestamp} itself, and
   * the JVM refuses to let a class loader define anything in a {@code java.*} package. Reading bytecode sidesteps it.
   */
  public void testComponentWithNonBaseModuleTypeIsReadableThroughTheFinder() throws Exception {
    LwRootContainer rootContainer =
      readForm("nonBaseModuleType/TestTimestampComponent.form", new AsmClassPropertiesProvider(myClassFinder));

    LwComponent component = (LwComponent)((LwContainer)rootContainer.getComponent(0)).getComponent(0);
    assertEquals("TimestampComponent", component.getComponentClassName());
    assertThat(component.getAssignedIntrospectedProperties()).extracting(LwIntrospectedProperty::getName)
      .containsExactlyInAnyOrder("label", "customValue");
  }

  /**
   * An enum-typed property carries the enum class name and the constant name, and nothing resolves the enum class:
   * loading it would run its static initializer, and would bind the value to this class loader rather than to the
   * one that owns the component instance the value is applied to.
   */
  public void testEnumPropertyIsReadAsADescriptor() throws Exception {
    LwRootContainer rootContainer =
      readForm("enumProperty/TestEnumPropertyComponent.form", new AsmClassPropertiesProvider(myClassFinder));

    assertNoPayloadExecuted();

    LwComponent component = (LwComponent)((LwContainer)rootContainer.getComponent(0)).getComponent(0);
    LwIntrospectedProperty[] properties = component.getAssignedIntrospectedProperties();
    assertThat(properties).extracting(LwIntrospectedProperty::getName).containsExactly("alignment");

    LwIntrospectedProperty property = properties[0];
    assertInstanceOf(property, LwIntroEnumProperty.class);
    assertEquals("Alignment", property.getPropertyClassName());

    EnumDescriptor value = (EnumDescriptor)component.getPropertyValue(property);
    assertEquals("Alignment", value.getClassName());
    assertEquals("RIGHT", value.getConstantName());
  }

  public void testUnknownComponentClassHasNoProperties() {
    // null (rather than an empty map) is what makes XmlReader render a per-component error placeholder
    assertNull(new AsmClassPropertiesProvider(myClassFinder).getLwProperties("no.such.Component"));
  }

  public void testOnlyPublicInstanceAccessorsAreProperties() {
    HashMap<String, LwIntrospectedProperty> properties =
      new AsmClassPropertiesProvider(myClassFinder).getLwProperties("NonPublicAccessorsComponent");
    assertNotNull(properties);

    assertThat(properties.keySet()).contains("publicProperty");
    assertThat(properties.keySet())
      .doesNotContain("privateProperty", "protectedProperty", "packageLocalProperty", "staticProperty");
  }

  private static LwRootContainer readHostileForm(PropertiesProvider provider) throws Exception {
    return readForm("beanInfoSafety/TestHostileComponent.form", provider);
  }

  private static LwRootContainer readForm(String testDataPath, PropertiesProvider provider) throws Exception {
    String formPath = PluginPathManager.getPluginHomePath("ui-designer") + "/testData/" + testDataPath;
    return Utils.getRootContainer(Files.readString(Path.of(formPath)), provider);
  }

  private void assertNoPayloadExecuted() throws Exception {
    if (Files.exists(myMarkerFile)) {
      fail("Component code was executed while reading the form: " + Files.readString(myMarkerFile).trim());
    }
  }

  private URL outputDirUrl() throws Exception {
    return new File(myOutputDir.toString()).toURI().toURL();
  }
}

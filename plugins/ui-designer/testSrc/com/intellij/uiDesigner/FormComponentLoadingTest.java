// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwContainer;
import com.intellij.uiDesigner.lw.LwIntrospectedProperty;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.radComponents.RadErrorComponent;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.sun.tools.javac.Main;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;
import javax.swing.JComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How the GUI Designer resolves the component classes a form names, from the module the form belongs to.
 */
public class FormComponentLoadingTest extends JavaCodeInsightFixtureTestCase {
  private static final String MARKER_PROPERTY = "uiDesigner.test.payloadMarker";

  private Path myMarkerFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    Path classesDir = FileUtil.createTempDirectory("uiDesignerHostileClasses", "").toPath();
    myMarkerFile = classesDir.resolve("payload-marker.txt");
    System.setProperty(MARKER_PROPERTY, myMarkerFile.toString());

    List<String> sources = List.of("beanInfoSafety/Payload.java",
                                   "beanInfoSafety/HostileComponent.java",
                                   "beanInfoSafety/HostileComponentBeanInfo.java",
                                   "enumProperty/Alignment.java",
                                   "enumProperty/EnumPropertyComponent.java");
    String[] args = new String[sources.size() + 2];
    args[0] = "-d";
    args[1] = classesDir.toString();
    for (int i = 0; i < sources.size(); i++) {
      args[i + 2] = getTestDataPath() + "/" + sources.get(i);
    }
    assertEquals("testData sources failed to compile", 0, Main.compile(args));

    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(classesDir);
    ModuleRootModificationUtil.addModuleLibrary(getModule(), VfsUtilCore.pathToUrl(classesDir.toString()));
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      System.clearProperty(MARKER_PROPERTY);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  /**
   * IDEA-392515: properties are discovered by reading bytecode, so reading a form never loads, initializes or
   * introspects the classes it names - in particular the {@code Introspector} is never given the chance to
   * instantiate a {@code <Component>BeanInfo} class from the project.
   */
  public void testReadingFormDoesNotExecuteComponentCode() throws Exception {
    VirtualFile formFile = copyForm("beanInfoSafety/TestHostileComponent.form");

    LwRootContainer rootContainer =
      LoaderFactory.getInstance(getProject()).readRootContainer(formFile, VfsUtilCore.loadText(formFile));

    if (Files.exists(myMarkerFile)) {
      fail("Component code was executed while reading the form: " + Files.readString(myMarkerFile).trim());
    }

    LwComponent component = (LwComponent)((LwContainer)rootContainer.getComponent(0)).getComponent(0);
    assertEquals("HostileComponent", component.getComponentClassName());
    assertThat(component.getAssignedIntrospectedProperties()).extracting(LwIntrospectedProperty::getName)
      .containsExactly("label");
  }

  /**
   * A module has no JDK of its own when it is misconfigured, and when the project was never loaded because it
   * is not trusted. Reading bytecode through the module classpath alone finds nothing then, so the standard
   * Swing classes have to come from the JDK the IDE itself runs on - otherwise every single component of
   * every form turns into an error placeholder.
   */
  public void testStandardComponentsResolveWithoutAModuleSdk() throws Exception {
    ModuleRootModificationUtil.setModuleSdk(getModule(), null);
    VirtualFile formFile = copyForm("beanInfoSafety/TestStandardComponent.form");

    LoaderFactory loaderFactory = LoaderFactory.getInstance(getProject());
    LwRootContainer lwRootContainer = loaderFactory.readRootContainer(formFile, VfsUtilCore.loadText(formFile));
    RadRootContainer rootContainer =
      XmlReader.createRoot(new MyModuleProvider(getModule()), lwRootContainer, loaderFactory.getLoader(formFile), null);

    RadComponent component = ((RadContainer)rootContainer.getComponent(0)).getComponent(0);
    assertFalse("form rendered as an error component: " + component, component instanceof RadErrorComponent);
    assertEquals(JButton.class, component.getDelegee().getClass());
  }

  /**
   * An enum-typed property is read as an {@link com.intellij.uiDesigner.lw.EnumDescriptor} and turned into a
   * constant by {@code IntroEnumProperty}, using the enum class of the design time class loader. Resolving the
   * enum while reading the form instead would bind the value to a different class loader than the component
   * instance it is applied to, and the setter would reject it with "argument type mismatch".
   */
  public void testEnumPropertyIsAppliedToTheComponent() throws Exception {
    VirtualFile formFile = copyForm("enumProperty/TestEnumPropertyComponent.form");

    LoaderFactory loaderFactory = LoaderFactory.getInstance(getProject());
    LwRootContainer lwRootContainer = loaderFactory.readRootContainer(formFile, VfsUtilCore.loadText(formFile));
    RadRootContainer rootContainer =
      XmlReader.createRoot(new MyModuleProvider(getModule()), lwRootContainer, loaderFactory.getLoader(formFile), null);

    RadComponent component = ((RadContainer)rootContainer.getComponent(0)).getComponent(0);
    assertFalse("form rendered as an error component: " + component, component instanceof RadErrorComponent);

    JComponent delegee = component.getDelegee();
    Object alignment = delegee.getClass().getMethod("getAlignment").invoke(delegee);
    assertEquals("RIGHT", String.valueOf(alignment));
  }

  private VirtualFile copyForm(String testDataPath) {
    return myFixture.copyFileToProject(testDataPath, StringUtil.getShortName(testDataPath, '/'));
  }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("ui-designer") + "/testData";
  }

  private static final class MyModuleProvider implements ModuleProvider {
    private final Module myModule;

    private MyModuleProvider(@NotNull Module module) {
      myModule = module;
    }

    @Override
    public Module getModule() {
      return myModule;
    }

    @Override
    public Project getProject() {
      return myModule.getProject();
    }
  }
}

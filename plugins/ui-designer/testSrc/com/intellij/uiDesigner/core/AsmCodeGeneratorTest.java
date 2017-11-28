/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.uiDesigner.core;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.components.BaseState;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.compiler.FormErrorInfo;
import com.intellij.uiDesigner.compiler.NestedFormLoader;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.CompiledClassPropertiesProvider;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.UIUtil;
import com.sun.tools.javac.Main;
import gnu.trove.TIntObjectHashMap;
import junit.framework.TestCase;
import kotlin.reflect.KDeclarationContainer;
import org.jetbrains.org.objectweb.asm.ClassWriter;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author yole
 */
public class AsmCodeGeneratorTest extends TestCase {
  private MyNestedFormLoader myNestedFormLoader;
  private MyClassFinder myClassFinder;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myNestedFormLoader = new MyNestedFormLoader();

    final String swingPath = PathUtil.getJarPathForClass(AbstractButton.class);

    List<URL> cp = new ArrayList<>();
    appendPath(cp, JBTabbedPane.class);
    appendPath(cp, TIntObjectHashMap.class);
    appendPath(cp, UIUtil.class);
    appendPath(cp, SystemInfoRt.class);
    appendPath(cp, ApplicationManager.class);
    appendPath(cp, PathManager.getResourceRoot(this.getClass(), "/messages/UIBundle.properties"));
    appendPath(cp, PathManager.getResourceRoot(this.getClass(), "/RuntimeBundle.properties"));
    appendPath(cp, GridLayoutManager.class); // forms_rt
    appendPath(cp, DataProvider.class);
    appendPath(cp, BaseState.class);
    appendPath(cp, KDeclarationContainer.class);
    myClassFinder = new MyClassFinder(
      new URL[] {new File(swingPath).toURI().toURL()},
      cp.toArray(new URL[cp.size()])
    );
  }

  private static void appendPath(Collection<URL> container, Class cls) throws MalformedURLException {
    final String path = PathUtil.getJarPathForClass(cls);
    appendPath(container, path);
  }

  private static void appendPath(Collection<URL> container, String path) throws MalformedURLException {
    container.add(new File(path).toURI().toURL());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myNestedFormLoader = null;
      final MyClassFinder classFinder = myClassFinder;
      if (classFinder != null) {
        classFinder.releaseResources();
        myClassFinder = null;
      }
    }
    finally {
      super.tearDown();
    }
  }

  private AsmCodeGenerator initCodeGenerator(final String formFileName, final String className) throws Exception {
    final String testDataPath = PluginPathManager.getPluginHomePath("ui-designer") + "/testData/";
    return initCodeGenerator(formFileName, className, testDataPath);
  }

  private AsmCodeGenerator initCodeGenerator(final String formFileName, final String className, final String testDataPath) throws Exception {
    String tmpPath = FileUtil.getTempDirectory();
    String formPath = testDataPath + formFileName;
    String javaPath = testDataPath + className + ".java";
    final int rc = Main.compile(new String[]{"-d", tmpPath, javaPath});
    
    assertEquals(0, rc);

    final String classPath = tmpPath + "/" + className + ".class";
    final File classFile = new File(classPath);
    
    assertTrue(classFile.exists());
    
    final LwRootContainer rootContainer = loadFormData(formPath);
    final AsmCodeGenerator codeGenerator = new AsmCodeGenerator(rootContainer, myClassFinder, myNestedFormLoader, false, new ClassWriter(ClassWriter.COMPUTE_FRAMES));
    final FileInputStream classStream = new FileInputStream(classFile);
    try {
      codeGenerator.patchClass(classStream);
    }
    finally {
      classStream.close();
      FileUtil.delete(classFile);
      final File[] inners = new File(tmpPath).listFiles((dir, name) -> name.startsWith(className + "$") && name.endsWith(".class"));
      if (inners != null) {
        for (File file : inners) FileUtil.delete(file);
      }
    }
    return codeGenerator;
  }

  private LwRootContainer loadFormData(final String formPath) throws Exception {
    String formData = FileUtil.loadFile(new File(formPath));
    final CompiledClassPropertiesProvider provider = new CompiledClassPropertiesProvider(getClass().getClassLoader());
    return Utils.getRootContainer(formData, provider);
  }

  private Class loadAndPatchClass(final String formFileName, final String className) throws Exception {
    final AsmCodeGenerator codeGenerator = initCodeGenerator(formFileName, className);

    byte[] patchedData = getVerifiedPatchedData(codeGenerator);

    /*
    FileOutputStream fos = new FileOutputStream("C:\\yole\\FormPreview27447\\MainPatched.class");
    fos.write(patchedData);
    fos.close();
    */

    myClassFinder.addClassDefinition(className, patchedData);
    return myClassFinder.getLoader().loadClass(className);
  }

  private static byte[] getVerifiedPatchedData(final AsmCodeGenerator codeGenerator) {
    byte[] patchedData = codeGenerator.getPatchedData();
    FormErrorInfo[] errors = codeGenerator.getErrors();
    FormErrorInfo[] warnings = codeGenerator.getWarnings();
    if (errors.length == 0 && warnings.length == 0) {
      assertNotNull("Class patching failed but no errors or warnings were returned", patchedData);
    }
    else if (errors.length > 0) {
      assertTrue(errors[0].getErrorMessage(), false);
    }
    else {
      assertTrue(warnings[0].getErrorMessage(), false);
    }
    return patchedData;
  }

  private JComponent getInstrumentedRootComponent(final String formFileName, final String className) throws Exception {
    Class cls = loadAndPatchClass(formFileName, className);
    Field rootComponentField = cls.getField("myRootComponent");
    rootComponentField.setAccessible(true);
    Object instance = cls.newInstance();
    return (JComponent)rootComponentField.get(instance);
  }

  public void testSimple() throws Exception {
    JComponent rootComponent = getInstrumentedRootComponent("TestSimple.form", "BindingTest");
    assertNotNull(rootComponent);
  }

  public void testNoSuchField() throws Exception {
    AsmCodeGenerator generator = initCodeGenerator("TestNoSuchField.form", "BindingTest");
    assertEquals("Cannot bind: field does not exist: BindingTest.myNoSuchField", generator.getErrors() [0].getErrorMessage());
  }

  public void testStaticField() throws Exception {
    AsmCodeGenerator generator = initCodeGenerator("TestStaticField.form", "BindingTest");
    assertEquals("Cannot bind: field is static: BindingTest.myStaticField", generator.getErrors() [0].getErrorMessage());
  }

  public void testFinalField() throws Exception {
    AsmCodeGenerator generator = initCodeGenerator("TestFinalField.form", "BindingTest");
    assertEquals("Cannot bind: field is final: BindingTest.myFinalField", generator.getErrors() [0].getErrorMessage());
  }

  public void testPrimitiveField() throws Exception {
    AsmCodeGenerator generator = initCodeGenerator("TestPrimitiveField.form", "BindingTest");
    assertEquals("Cannot bind: field is of primitive type: BindingTest.myIntField", generator.getErrors() [0].getErrorMessage());
  }

  public void testIncompatibleTypeField() throws Exception {
    AsmCodeGenerator generator = initCodeGenerator("TestIncompatibleTypeField.form", "BindingTest");
    assertEquals("Cannot bind: Incompatible types. Cannot assign javax.swing.JPanel to field BindingTest.myStringField", generator.getErrors() [0].getErrorMessage());
  }

  public void testGridLayout() throws Exception {
    JComponent rootComponent = getInstrumentedRootComponent("TestGridConstraints.form", "BindingTest");
    final LayoutManager layout = rootComponent.getLayout();
    assertTrue(isInstanceOf(layout, GridLayoutManager.class.getName()));


    assertEquals(1, invokeMethod(layout, "getRowCount"));
    assertEquals(1, invokeMethod(layout, "getColumnCount"));
  }

  private static boolean isInstanceOf(Object object, final String className) throws ClassNotFoundException {
    final Class<?> ethalon = object.getClass().getClassLoader().loadClass(className);
    return ethalon.isAssignableFrom(object.getClass());
  }

  private static Object invokeMethod(Object obj, String methodName) throws InvocationTargetException, IllegalAccessException {
    return invokeMethod(obj, methodName, ArrayUtil.EMPTY_CLASS_ARRAY, ArrayUtil.EMPTY_OBJECT_ARRAY);
  }

  private static Object invokeMethod(Object obj, String methodName, Class[] params, Object[] args) throws
                                                                                                   InvocationTargetException, IllegalAccessException {
    final Method method = findMethod(obj.getClass(), methodName, params);
    return method.invoke(obj, args);
  }

  private static Method findMethod(Class<?> aClass, String methodName, Class[] params) {
    try {
      final Method method = aClass.getDeclaredMethod(methodName, params);
      method.setAccessible(true);
      return method;
    }
    catch (NoSuchMethodException ignored) {
    }
    final Class<?> parent = aClass.getSuperclass();
    if (parent == null) {
      return null;
    }
    return findMethod(parent, methodName, params);
  }

  public void testCardLayout() throws Exception {
    JComponent rootComponent = getInstrumentedRootComponent("TestCardLayout.form", "BindingTest");
    assertTrue(rootComponent.getLayout() instanceof CardLayout);
    CardLayout cardLayout = (CardLayout) rootComponent.getLayout();
    assertEquals(10, cardLayout.getHgap());
    assertEquals(20, cardLayout.getVgap());
  }

  public void testCardLayoutShow() throws Exception {
    JComponent rootComponent = getInstrumentedRootComponent("TestCardLayoutShow.form", "BindingTest");
    assertTrue(rootComponent.getLayout() instanceof CardLayout);
    assertThat(rootComponent.getComponentCount()).isEqualTo(2);
    assertFalse(rootComponent.getComponent(0).isVisible());
    assertTrue(rootComponent.getComponent(1).isVisible());
  }

  public void testGridConstraints() throws Exception {
    JComponent rootComponent = getInstrumentedRootComponent("TestGridConstraints.form", "BindingTest");
    assertEquals(1, rootComponent.getComponentCount());
    final LayoutManager layout = rootComponent.getLayout();
    assertTrue(isInstanceOf(layout, GridLayoutManager.class.getName()));

    final Object constraints = invokeMethod(layout, "getConstraints", new Class[] {int.class}, new Object[] {0});
    assertTrue(isInstanceOf(constraints, GridConstraints.class.getName()));

    assertEquals(1, invokeMethod(constraints, "getColSpan"));
    assertEquals(1, invokeMethod(constraints, "getRowSpan"));
  }

  public void testIntProperty() throws Exception {
    JComponent rootComponent = getInstrumentedRootComponent("TestIntProperty.form", "BindingTest");
    assertEquals(1, rootComponent.getComponentCount());
    JTextField textField = (JTextField) rootComponent.getComponent(0);
    assertEquals(37, textField.getColumns());
    assertEquals(false, textField.isEnabled());
  }

  public void testDoubleProperty() throws Exception {
    JSplitPane splitPane = (JSplitPane)getInstrumentedRootComponent("TestDoubleProperty.form", "BindingTest");
    assertEquals(0.1f, splitPane.getResizeWeight(), 0.001f);
  }

  public void testStringProperty() throws Exception {
    JComponent rootComponent = getInstrumentedRootComponent("TestGridConstraints.form", "BindingTest");
    JButton btn = (JButton) rootComponent.getComponent(0);
    assertEquals("MyTestButton", btn.getText());
  }

  public void testSplitPane() throws Exception {
    JSplitPane splitPane = (JSplitPane)getInstrumentedRootComponent("TestSplitPane.form", "BindingTest");
    assertTrue(splitPane.getLeftComponent() instanceof JLabel);
    assertTrue(splitPane.getRightComponent() instanceof JCheckBox);
  }

  public void testTabbedPane() throws Exception {
    JTabbedPane tabbedPane = (JTabbedPane) getInstrumentedRootComponent("TestTabbedPane.form", "BindingTest");
    assertEquals(2, tabbedPane.getTabCount());
    assertEquals("First", tabbedPane.getTitleAt(0));
    assertEquals("Test Value", tabbedPane.getTitleAt(1));
    assertTrue(tabbedPane.getComponentAt(0) instanceof JLabel);
    assertTrue(tabbedPane.getComponentAt(1) instanceof JButton);
  }

  public void testScrollPane() throws Exception {
    JScrollPane scrollPane = (JScrollPane)getInstrumentedRootComponent("TestScrollPane.form", "BindingTest");
    assertTrue(scrollPane.getViewport().getView() instanceof JList);
  }

  public void testBorder() throws Exception {
    JPanel panel = (JPanel) getInstrumentedRootComponent("TestBorder.form", "BindingTest");
    assertTrue(panel.getBorder() instanceof TitledBorder);
    TitledBorder border = (TitledBorder) panel.getBorder();
    assertEquals("BorderTitle", border.getTitle());
    assertTrue(border.getBorder().toString(), border.getBorder() instanceof EtchedBorder);
  }

  public void testMnemonic() throws Exception {
    JPanel panel = (JPanel) getInstrumentedRootComponent("TestMnemonics.form", "BindingTest");
    JLabel label = (JLabel) panel.getComponent(0);
    assertEquals("Mnemonic", label.getText());
    assertEquals('M', label.getDisplayedMnemonic());
    assertEquals(3, label.getDisplayedMnemonicIndex());
  }

  public void testMnemonicFromProperty() throws Exception {
    JPanel panel = (JPanel) getInstrumentedRootComponent("TestMnemonicsProperty.form", "BindingTest");
    JLabel label = (JLabel) panel.getComponent(0);
    assertEquals("Mnemonic", label.getText());
    assertEquals('M', label.getDisplayedMnemonic());
    assertEquals(3, label.getDisplayedMnemonicIndex());
  }

  public void testGridBagLayout() throws Exception {
    JPanel panel = (JPanel) getInstrumentedRootComponent("TestGridBag.form", "BindingTest");
    assertTrue(panel.getLayout() instanceof GridBagLayout);
    GridBagLayout gridBag = (GridBagLayout) panel.getLayout();
    JButton btn = (JButton) panel.getComponent(0);
    GridBagConstraints gbc = gridBag.getConstraints(btn);
    assertNotNull(gbc);
    assertEquals(2, gbc.gridheight);
    assertEquals(2, gbc.gridwidth);
    assertEquals(1.0, gbc.weightx, 0.01);
    assertEquals(new Insets(1, 2, 3, 4), gbc.insets);
    assertEquals(GridBagConstraints.HORIZONTAL, gbc.fill);
    assertEquals(GridBagConstraints.NORTHWEST, gbc.anchor);
  }

  public void testGridBagSpacer() throws Exception {
    JPanel panel = (JPanel) getInstrumentedRootComponent("TestGridBagSpacer.form", "BindingTest");
    assertTrue(panel.getLayout() instanceof GridBagLayout);
    assertTrue(panel.getComponent(0) instanceof JLabel);
    assertTrue(panel.getComponent(1) instanceof JPanel);

    GridBagLayout gridBag = (GridBagLayout) panel.getLayout();
    GridBagConstraints gbc = gridBag.getConstraints(panel.getComponent(0));
    assertEquals(0.0, gbc.weightx, 0.01);
    assertEquals(0.0, gbc.weighty, 0.01);
    gbc = gridBag.getConstraints(panel.getComponent(1));
    assertEquals(0.0, gbc.weightx, 0.01);
    assertEquals(1.0, gbc.weighty, 0.01);
  }

  public void testLabelFor() throws Exception {
    JPanel panel = (JPanel) getInstrumentedRootComponent("TestLabelFor.form", "BindingTest");
    JTextField textField = (JTextField) panel.getComponent(0);
    JLabel label = (JLabel) panel.getComponent(1);
    assertEquals(textField, label.getLabelFor());
  }

  public void testFieldReference() throws Exception {
    Class cls = loadAndPatchClass("TestFieldReference.form", "FieldReferenceTest");
    JPanel instance = (JPanel) cls.newInstance();
    assertEquals(1, instance.getComponentCount());
  }

  public void testChainedConstructor() throws Exception {
    Class cls = loadAndPatchClass("TestChainedConstructor.form", "ChainedConstructorTest");
    Field scrollPaneField = cls.getField("myScrollPane");
    Object instance = cls.newInstance();
    JScrollPane scrollPane = (JScrollPane) scrollPaneField.get(instance);
    assertNotNull(scrollPane.getViewport().getView());
  }

  public void testConditionalMethodCall() throws Exception {
    JPanel panel = (JPanel) getInstrumentedRootComponent("TestConditionalMethodCall.form", "ConditionalMethodCallTest");
    assertNotNull(panel);
  }

  public void testMethodCallInSuper() throws Exception {
    // todo[yole] make this test work in headless
    if (!GraphicsEnvironment.isHeadless()) {
      Class cls = loadAndPatchClass("TestMethodCallInSuper.form", "MethodCallInSuperTest");
      JDialog instance = (JDialog) cls.newInstance();
      assertEquals(1, instance.getContentPane().getComponentCount());
    }
  }

  public void testClientProp() throws Exception {  // IDEA-46372
    JComponent rootComponent = getInstrumentedRootComponent("TestClientProp.form", "BindingTest");
    assertEquals(1, rootComponent.getComponentCount());
    JTable table = (JTable) rootComponent.getComponent(0);
    assertSame(Boolean.TRUE, table.getClientProperty("terminateEditOnFocusLost"));
  }

  public void testIdeadev14081() throws Exception {
    // NOTE: That doesn't really reproduce the bug as it's dependent on a particular instrumentation sequence used during form preview
    // (the nested form is instrumented with a new AsmCodeGenerator instance directly in the middle of instrumentation of the current form)
    final String testDataPath = PluginPathManager.getPluginHomePath("ui-designer") + File.separatorChar + "testData" + File.separatorChar +
      File.separatorChar + "formEmbedding" + File.separatorChar + "Ideadev14081" + File.separatorChar;
    AsmCodeGenerator embeddedClassGenerator = initCodeGenerator("Embedded.form", "Embedded", testDataPath);
    byte[] embeddedPatchedData = getVerifiedPatchedData(embeddedClassGenerator);
    myClassFinder.addClassDefinition("Embedded", embeddedPatchedData);
    myNestedFormLoader.registerNestedForm("Embedded.form", testDataPath + "Embedded.form");
    AsmCodeGenerator mainClassGenerator = initCodeGenerator("Main.form", "Main", testDataPath);
    byte[] mainPatchedData = getVerifiedPatchedData(mainClassGenerator);

    /*
    FileOutputStream fos = new FileOutputStream("C:\\yole\\FormPreview27447\\MainPatched.class");
    fos.write(mainPatchedData);
    fos.close();
    */

    myClassFinder.addClassDefinition("Main", mainPatchedData);
    final Class mainClass = myClassFinder.getLoader().loadClass("Main");
    Object instance = mainClass.newInstance();
    assert instance != null : mainClass;
  }

  private static class MyClassFinder extends InstrumentationClassFinder {
    private static final String TEST_PROPERTY_CONTENT = "test=Test Value\nmnemonic=Mne&monic";
    private final byte[] myTestProperties = Charset.defaultCharset().encode(TEST_PROPERTY_CONTENT).array();
    private final Map<String, byte[]> myClassData = new HashMap<>();

    private MyClassFinder(URL[] platformUrls, URL[] classpathUrls) {
      super(platformUrls, classpathUrls);
    }

    public void addClassDefinition(String name, byte[] bytes) {
      myClassData.put(name.replace('.', '/'), bytes);
    }

    @Override
    protected InputStream lookupClassBeforeClasspath(String internalClassName) {
      final byte[] bytes = myClassData.get(internalClassName);
      if (bytes != null) {
        return new ByteArrayInputStream(bytes);
      }
      return null;
    }

    @Override
    public InputStream getResourceAsStream(String name) throws IOException {
      if (name.equals("TestProperties.properties")) {
        return new ByteArrayInputStream(myTestProperties, 0, TEST_PROPERTY_CONTENT.length());
      }
      return super.getResourceAsStream(name);
    }
  }

  private class MyNestedFormLoader implements NestedFormLoader {
    private final Map<String, String> myFormMap = new HashMap<>();

    public void registerNestedForm(String formName, String fileName) {
      myFormMap.put(formName, fileName);
    }

    @Override
    public LwRootContainer loadForm(String formFileName) throws Exception {
      final String fileName = myFormMap.get(formFileName);
      if (fileName != null) {
        return loadFormData(fileName);
      }
      throw new UnsupportedOperationException("No nested form found for name " + formFileName);
    }

    @Override
    public String getClassToBindName(LwRootContainer container) {
      return container.getClassToBind();
    }
  }
}

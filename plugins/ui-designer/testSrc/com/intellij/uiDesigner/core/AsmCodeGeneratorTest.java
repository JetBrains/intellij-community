/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.compiler.FormErrorInfo;
import com.intellij.uiDesigner.compiler.NestedFormLoader;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.CompiledClassPropertiesProvider;
import com.intellij.uiDesigner.lw.LwRootContainer;
import junit.framework.TestCase;
import org.objectweb.asm.ClassWriter;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public class AsmCodeGeneratorTest extends TestCase {
  private MyNestedFormLoader myNestedFormLoader;
  private MyClassLoader myClassLoader;

  protected void setUp() throws Exception {
    super.setUp();
    myNestedFormLoader = new MyNestedFormLoader();
    myClassLoader = new MyClassLoader(getClass().getClassLoader());
  }

  protected void tearDown() throws Exception {
    myClassLoader = null;
    myNestedFormLoader = null;
    super.tearDown();
  }

  private AsmCodeGenerator initCodeGenerator(final String formFileName, final String className) throws Exception {
    final String testDataPath = PluginPathManager.getPluginHomePath("ui-designer") + "/testData/";
    return initCodeGenerator(formFileName, className, testDataPath);
  }

  private AsmCodeGenerator initCodeGenerator(final String formFileName, final String className, final String testDataPath) throws Exception {
    String tmpPath = FileUtil.getTempDirectory();
    String formPath = testDataPath + formFileName;
    String javaPath = testDataPath + className + ".java";
    com.sun.tools.javac.Main.compile(new String[] { "-d", tmpPath, javaPath } );

    String classPath = tmpPath + "/" + className + ".class";
    final LwRootContainer rootContainer = loadFormData(formPath);
    final AsmCodeGenerator codeGenerator = new AsmCodeGenerator(rootContainer, myClassLoader, myNestedFormLoader, false,
                                                                new ClassWriter(ClassWriter.COMPUTE_FRAMES));
    final FileInputStream classStream = new FileInputStream(classPath);
    try {
      codeGenerator.patchClass(classStream);
    }
    finally {
      classStream.close();
      FileUtil.delete(new File(classPath));
      final File[] inners = new File(tmpPath).listFiles(new FilenameFilter() {
        public boolean accept(File dir, String name) {
          return name.startsWith(className + "$") && name.endsWith(".class");
        }
      });
      if (inners != null) {
        for (File file : inners) FileUtil.delete(file);
      }
    }
    return codeGenerator;
  }

  private LwRootContainer loadFormData(final String formPath) throws Exception {
    String formData = new String(FileUtil.loadFileText(new File(formPath)));
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

    return myClassLoader.doDefineClass(className, patchedData);
  }

  private byte[] getVerifiedPatchedData(final AsmCodeGenerator codeGenerator) {
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
    assertTrue(rootComponent.getLayout() instanceof GridLayoutManager);
    GridLayoutManager gridLayout = (GridLayoutManager) rootComponent.getLayout();
    assertEquals(1, gridLayout.getRowCount());
    assertEquals(1, gridLayout.getColumnCount());
  }

  public void testGridConstraints() throws Exception {
    JComponent rootComponent = getInstrumentedRootComponent("TestGridConstraints.form", "BindingTest");
    assertEquals(1, rootComponent.getComponentCount());
    GridLayoutManager gridLayout = (GridLayoutManager) rootComponent.getLayout();
    final GridConstraints constraints = gridLayout.getConstraints(0);
    assertEquals(1, constraints.getColSpan());
    assertEquals(1, constraints.getRowSpan());
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
    assertTrue(border.getBorder() instanceof EtchedBorder);
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
    Class cls = loadAndPatchClass("TestMethodCallInSuper.form", "MethodCallInSuperTest");
    JDialog instance = (JDialog) cls.newInstance();
    assertEquals(1, instance.getContentPane().getComponentCount());
  }

  public void testIdeadev14081() throws Exception {
    // NOTE: That doesn't really reproduce the bug as it's dependent on a particular instrumentation sequence used during form preview
    // (the nested form is instrumented with a new AsmCodeGenerator instance directly in the middle of instrumentation of the current form)
    final String testDataPath = PluginPathManager.getPluginHomePath("ui-designer") + File.separatorChar + "testData" + File.separatorChar +
      File.separatorChar + "formEmbedding" + File.separatorChar + "Ideadev14081" + File.separatorChar;
    AsmCodeGenerator embeddedClassGenerator = initCodeGenerator("Embedded.form", "Embedded", testDataPath);
    byte[] embeddedPatchedData = getVerifiedPatchedData(embeddedClassGenerator);
    myClassLoader.doDefineClass("Embedded", embeddedPatchedData);
    myNestedFormLoader.registerNestedForm("Embedded.form", testDataPath + "Embedded.form");
    AsmCodeGenerator mainClassGenerator = initCodeGenerator("Main.form", "Main", testDataPath);
    byte[] mainPatchedData = getVerifiedPatchedData(mainClassGenerator);

    /*
    FileOutputStream fos = new FileOutputStream("C:\\yole\\FormPreview27447\\MainPatched.class");
    fos.write(mainPatchedData);
    fos.close();
    */

    final Class mainClass = myClassLoader.doDefineClass("Main", mainPatchedData);
    Object instance = mainClass.newInstance();
  }

  private static class MyClassLoader extends ClassLoader {
    private final byte[] myTestProperties = Charset.defaultCharset().encode(TEST_PROPERTY_CONTENT).array();
    private static final String TEST_PROPERTY_CONTENT = "test=Test Value\nmnemonic=Mne&monic";

    public MyClassLoader(ClassLoader parent) {
      super(parent);
    }

    public Class doDefineClass(String name, byte[] data) {
      return defineClass(name, data, 0, data.length);
    }

    public Class<?> loadClass(String name) throws ClassNotFoundException {
      return super.loadClass(name);
    }

    public InputStream getResourceAsStream(String name) {
      if (name.equals("TestProperties.properties")) {
        return new ByteArrayInputStream(myTestProperties, 0, TEST_PROPERTY_CONTENT.length());
      }
      return super.getResourceAsStream(name);
    }
  }

  private class MyNestedFormLoader implements NestedFormLoader {
    private final Map<String, String> myFormMap = new HashMap<String, String>();

    public void registerNestedForm(String formName, String fileName) {
      myFormMap.put(formName, fileName);
    }

    public LwRootContainer loadForm(String formFileName) throws Exception {
      final String fileName = myFormMap.get(formFileName);
      if (fileName != null) {
        return loadFormData(fileName);
      }
      throw new UnsupportedOperationException("No nested form found for name " + formFileName);
    }

    public String getClassToBindName(LwRootContainer container) {
      return container.getClassToBind();
    }
  }
}

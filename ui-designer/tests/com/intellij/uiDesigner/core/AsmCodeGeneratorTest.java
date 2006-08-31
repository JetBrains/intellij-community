package com.intellij.uiDesigner.core;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.compiler.FormErrorInfo;
import com.intellij.uiDesigner.lw.CompiledClassPropertiesProvider;
import com.intellij.uiDesigner.lw.LwRootContainer;
import junit.framework.TestCase;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.Charset;

/**
 * @author yole
 */
public class AsmCodeGeneratorTest extends TestCase {
  private AsmCodeGenerator initCodeGenerator(final String formFileName, final String className) throws Exception {
    final String testDataPath = PathManagerEx.getTestDataPath() + File.separatorChar + "uiDesigner" + File.separatorChar;
    String formPath = testDataPath + formFileName;
    String classPath = testDataPath + className;
    String formData = new String(FileUtil.loadFileText(new File(formPath)));
    final ClassLoader classLoader = getClass().getClassLoader();
    final CompiledClassPropertiesProvider provider = new CompiledClassPropertiesProvider(classLoader);
    final LwRootContainer rootContainer = Utils.getRootContainer(formData, provider);
    final AsmCodeGenerator codeGenerator = new AsmCodeGenerator(rootContainer, classLoader, null, false);
    final FileInputStream classStream = new FileInputStream(classPath);
    try {
      codeGenerator.patchClass(classStream);
    }
    finally {
      classStream.close();
    }
    return codeGenerator;
  }

  private Class loadAndPatchClass(final String formFileName, final String className) throws Exception {
    final AsmCodeGenerator codeGenerator = initCodeGenerator(formFileName, className);

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

    /*
    FileOutputStream fos = new FileOutputStream(testDataPath + "BindingTestPatched.class");
    fos.write(patchedData);
    fos.close();
    */

    return new MyClassLoader(AsmCodeGeneratorTest.class.getClassLoader()).doDefineClass("BindingTest", patchedData);
  }

  private JComponent getInstrumentedRootComponent(final String formFileName, final String className) throws Exception {
    Class cls = loadAndPatchClass(formFileName, className);
    Field rootComponentField = cls.getField("myRootComponent");
    rootComponentField.setAccessible(true);
    Object instance = cls.newInstance();
    JComponent rootComponent = (JComponent)rootComponentField.get(instance);
    return rootComponent;
  }

  public void testSimple() throws Exception {
    JComponent rootComponent = getInstrumentedRootComponent("TestSimple.form", "BindingTest.class");
    assertNotNull(rootComponent);
  }

  public void testNoSuchField() throws Exception {
    AsmCodeGenerator generator = initCodeGenerator("TestNoSuchField.form", "BindingTest.class");
    assertEquals("Cannot bind: field does not exist: BindingTest.myNoSuchField", generator.getErrors() [0].getErrorMessage());
  }

  public void testStaticField() throws Exception {
    AsmCodeGenerator generator = initCodeGenerator("TestStaticField.form", "BindingTest.class");
    assertEquals("Cannot bind: field is static: BindingTest.myStaticField", generator.getErrors() [0].getErrorMessage());
  }

  public void testFinalField() throws Exception {
    AsmCodeGenerator generator = initCodeGenerator("TestFinalField.form", "BindingTest.class");
    assertEquals("Cannot bind: field is final: BindingTest.myFinalField", generator.getErrors() [0].getErrorMessage());
  }

  public void testPrimitiveField() throws Exception {
    AsmCodeGenerator generator = initCodeGenerator("TestPrimitiveField.form", "BindingTest.class");
    assertEquals("Cannot bind: field is of primitive type: BindingTest.myIntField", generator.getErrors() [0].getErrorMessage());
  }

  public void testIncompatibleTypeField() throws Exception {
    AsmCodeGenerator generator = initCodeGenerator("TestIncompatibleTypeField.form", "BindingTest.class");
    assertEquals("Cannot bind: Incompatible types. Cannot assign javax.swing.JPanel to field BindingTest.myStringField", generator.getErrors() [0].getErrorMessage());
  }

  public void testGridLayout() throws Exception {
    JComponent rootComponent = getInstrumentedRootComponent("TestGridConstraints.form", "BindingTest.class");
    assertTrue(rootComponent.getLayout() instanceof GridLayoutManager);
    GridLayoutManager gridLayout = (GridLayoutManager) rootComponent.getLayout();
    assertEquals(1, gridLayout.getRowCount());
    assertEquals(1, gridLayout.getColumnCount());
  }

  public void testGridConstraints() throws Exception {
    JComponent rootComponent = getInstrumentedRootComponent("TestGridConstraints.form", "BindingTest.class");
    assertEquals(1, rootComponent.getComponentCount());
    GridLayoutManager gridLayout = (GridLayoutManager) rootComponent.getLayout();
    final GridConstraints constraints = gridLayout.getConstraints(0);
    assertEquals(1, constraints.getColSpan());
    assertEquals(1, constraints.getRowSpan());
  }

  public void testIntProperty() throws Exception {
    JComponent rootComponent = getInstrumentedRootComponent("TestIntProperty.form", "BindingTest.class");
    assertEquals(1, rootComponent.getComponentCount());
    JTextField textField = (JTextField) rootComponent.getComponent(0);
    assertEquals(37, textField.getColumns());
    assertEquals(false, textField.isEnabled());
  }

  public void testDoubleProperty() throws Exception {
    JSplitPane splitPane = (JSplitPane)getInstrumentedRootComponent("TestDoubleProperty.form", "BindingTest.class");
    assertEquals(0.1f, splitPane.getResizeWeight(), 0.001f);
  }

  public void testStringProperty() throws Exception {
    JComponent rootComponent = getInstrumentedRootComponent("TestGridConstraints.form", "BindingTest.class");
    JButton btn = (JButton) rootComponent.getComponent(0);
    assertEquals("MyTestButton", btn.getText());
  }

  public void testSplitPane() throws Exception {
    JSplitPane splitPane = (JSplitPane)getInstrumentedRootComponent("TestSplitPane.form", "BindingTest.class");
    assertTrue(splitPane.getLeftComponent() instanceof JLabel);
    assertTrue(splitPane.getRightComponent() instanceof JCheckBox);
  }

  public void testTabbedPane() throws Exception {
    JTabbedPane tabbedPane = (JTabbedPane) getInstrumentedRootComponent("TestTabbedPane.form", "BindingTest.class");
    assertEquals(2, tabbedPane.getTabCount());
    assertEquals("First", tabbedPane.getTitleAt(0));
    assertEquals("Test Value", tabbedPane.getTitleAt(1));
    assertTrue(tabbedPane.getComponentAt(0) instanceof JLabel);
    assertTrue(tabbedPane.getComponentAt(1) instanceof JButton);
  }

  public void testScrollPane() throws Exception {
    JScrollPane scrollPane = (JScrollPane)getInstrumentedRootComponent("TestScrollPane.form", "BindingTest.class");
    assertTrue(scrollPane.getViewport().getView() instanceof JList);
  }

  public void testBorder() throws Exception {
    JPanel panel = (JPanel) getInstrumentedRootComponent("TestBorder.form", "BindingTest.class");
    assertTrue(panel.getBorder() instanceof TitledBorder);
    TitledBorder border = (TitledBorder) panel.getBorder();
    assertEquals("BorderTitle", border.getTitle());
    assertTrue(border.getBorder() instanceof EtchedBorder);
  }

  public void testMnemonic() throws Exception {
    JPanel panel = (JPanel) getInstrumentedRootComponent("TestMnemonics.form", "BindingTest.class");
    JLabel label = (JLabel) panel.getComponent(0);
    assertEquals("Mnemonic", label.getText());
    assertEquals('M', label.getDisplayedMnemonic());
    assertEquals(3, label.getDisplayedMnemonicIndex());
  }

  public void testMnemonicFromProperty() throws Exception {
    JPanel panel = (JPanel) getInstrumentedRootComponent("TestMnemonicsProperty.form", "BindingTest.class");
    JLabel label = (JLabel) panel.getComponent(0);
    assertEquals("Mnemonic", label.getText());
    assertEquals('M', label.getDisplayedMnemonic());
    assertEquals(3, label.getDisplayedMnemonicIndex());
  }

  public void testGridBagLayout() throws Exception {
    JPanel panel = (JPanel) getInstrumentedRootComponent("TestGridBag.form", "BindingTest.class");
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
    JPanel panel = (JPanel) getInstrumentedRootComponent("TestGridBagSpacer.form", "BindingTest.class");
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
    JPanel panel = (JPanel) getInstrumentedRootComponent("TestLabelFor.form", "BindingTest.class");
    JTextField textField = (JTextField) panel.getComponent(0);
    JLabel label = (JLabel) panel.getComponent(1);
    assertEquals(textField, label.getLabelFor());
  }

  private static class MyClassLoader extends ClassLoader {
    private byte[] myTestProperties = Charset.defaultCharset().encode(TEST_PROPERTY_CONTENT).array();
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
}

package com.intellij.uiDesigner.core;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.compiler.GridLayoutCodeGenerator;
import com.intellij.uiDesigner.lw.CompiledClassPropertiesProvider;
import com.intellij.uiDesigner.lw.LwRootContainer;
import junit.framework.TestCase;

import javax.swing.*;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 17.11.2005
 * Time: 16:25:33
 * To change this template use File | Settings | File Templates.
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
    final AsmCodeGenerator codeGenerator = new AsmCodeGenerator(rootContainer, classLoader, new GridLayoutCodeGenerator());
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
    String[] errors = codeGenerator.getErrors();
    String[] warnings = codeGenerator.getWarnings();
    if (errors.length == 0 && warnings.length == 0) {
      assertNotNull("Class patching failed but no errors or warnings were returned", patchedData);
    }
    else if (errors.length > 0) {
      assertTrue(errors[0], false);
    }
    else {
      assertTrue(warnings[0], false);
    }

    /*
    FileOutputStream fos = new FileOutputStream(testDataPath + "BindingTestPatched.class");
    fos.write(patchedData);
    fos.close();
    */

    return new MyClassLoader().doDefineClass("BindingTest", patchedData);
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
    assertEquals("Cannot bind: field does not exist: BindingTest.myNoSuchField", generator.getErrors() [0]);
  }

  public void testStaticField() throws Exception {
    AsmCodeGenerator generator = initCodeGenerator("TestStaticField.form", "BindingTest.class");
    assertEquals("Cannot bind: field is static: BindingTest.myStaticField", generator.getErrors() [0]);
  }

  public void testFinalField() throws Exception {
    AsmCodeGenerator generator = initCodeGenerator("TestFinalField.form", "BindingTest.class");
    assertEquals("Cannot bind: field is final: BindingTest.myFinalField", generator.getErrors() [0]);
  }

  public void testPrimitiveField() throws Exception {
    AsmCodeGenerator generator = initCodeGenerator("TestPrimitiveField.form", "BindingTest.class");
    assertEquals("Cannot bind: field is of primitive type: BindingTest.myIntField", generator.getErrors() [0]);
  }

  public void testIncompatibleTypeField() throws Exception {
    AsmCodeGenerator generator = initCodeGenerator("TestIncompatibleTypeField.form", "BindingTest.class");
    assertEquals("Cannot bind: Incompatible types. Cannot assign javax.swing.JPanel to field BindingTest.myStringField", generator.getErrors() [0]);
  }

  public void testGridLayout() throws Exception {
    JComponent rootComponent = getInstrumentedRootComponent("TestGridConstraints.form", "BindingTest.class");
    assertTrue(rootComponent.getLayout() instanceof GridLayoutManager);
    GridLayoutManager gridLayout = (GridLayoutManager) rootComponent.getLayout();
    assertEquals(1, gridLayout.getRowCount());
    assertEquals(1, gridLayout.getColumnCount());
  }

  private class MyClassLoader extends ClassLoader {
    public Class doDefineClass(String name, byte[] data) {
      return defineClass(name, data, 0, data.length);
    }
  }
}

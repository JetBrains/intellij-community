package com.jetbrains.python;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.fixtures.PyResolveTestCase;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.toolbox.Maybe;
import junit.framework.TestSuite;

/**
 * Tests property API.
 * User: dcheryasov
 * Date: Jun 30, 2010 8:09:12 AM
 */
public class PyPropertyTestSuite {
  public static TestSuite suite() {
    return new TestSuite(PyClassicPropertyTest.class);
  }

  abstract static class PyPropertyTest  extends PyResolveTestCase {
    protected PyClass myClass;
    protected LanguageLevel myLanguageLevel = LanguageLevel.PYTHON26;

    abstract String getFileName();

    @Override
    protected void setUp() throws Exception {
      super.setUp();
      PsiReference ref = configureByFile("property/"+ getFileName());
      final Project project = ref.getElement().getContainingFile().getProject();
      project.putUserData(PyBuiltinCache.TEST_SDK, PythonMockSdk.findOrCreate());
      PythonLanguageLevelPusher.setForcedLanguageLevel(project, myLanguageLevel);
      PsiElement elt = ref.resolve();
      assertInstanceOf(elt, PyExpression.class);
      PyType type = ((PyExpression)elt).getType(TypeEvalContext.slow());
      assertInstanceOf(type, PyClassType.class);
      myClass = ((PyClassType)type).getPyClass();
      assertNotNull(myClass);
    }
  }

  public static class PyClassicPropertyTest extends PyPropertyTest {

    public PyClassicPropertyTest() {
      super();
    }

    @Override
    String getFileName() {
      return "Classic.py";
    }

    public void testV1() throws Exception {
      Property p;
      Maybe<PyFunction> accessor;
      p = myClass.findProperty("v1");
      assertNotNull(p);
      assertNull(p.getDoc());
      PyTargetExpression site = p.getDefinitionSite();
      assertEquals("v1", site.getText());

      accessor = p.getGetter();
      assertTrue(accessor.isDefined());
      assertNotNull(accessor.value());
      assertEquals("getter", accessor.value().getName());

      accessor = p.getSetter();
      assertTrue(accessor.isDefined());
      assertNotNull(accessor.value());
      assertEquals("setter", accessor.value().getName());

      accessor = p.getDeleter();
      assertTrue(accessor.isDefined());
      assertNull(accessor.value());
    }

    public void testV2() throws Exception {
      Property p;
      Maybe<PyFunction> accessor;
      p = myClass.findProperty("v2");
      assertNotNull(p);
      assertEquals("doc of v2", p.getDoc());
      PyTargetExpression site = p.getDefinitionSite();
      assertEquals("v2", site.getText());

      accessor = p.getGetter();
      assertTrue(accessor.isDefined());
      assertNotNull(accessor.value());
      assertEquals("getter", accessor.value().getName());

      accessor = p.getSetter();
      assertTrue(accessor.isDefined());
      assertNotNull(accessor.value());
      assertEquals("setter", accessor.value().getName());

      accessor = p.getDeleter();
      assertTrue(accessor.isDefined());
      assertNotNull(accessor.value());
      assertEquals("deleter", accessor.value().getName());
    }

    public void testV3() throws Exception {
      Property p;
      Maybe<PyFunction> accessor;
      p = myClass.findProperty("v3");
      assertNotNull(p);
      assertNull(p.getDoc());
      PyTargetExpression site = p.getDefinitionSite();
      assertEquals("v3", site.getText());

      accessor = p.getGetter();
      assertFalse(accessor.isDefined());

      accessor = p.getSetter();
      assertTrue(accessor.isDefined());
      assertNull(accessor.value());

      accessor = p.getDeleter();
      assertTrue(accessor.isDefined());
      assertNotNull(accessor.value());
      assertEquals("deleter", accessor.value().getName());
    }

    /* NOTE: we don't support this yet
    public void testV4() throws Exception {
      Property p;
      Maybe<PyFunction> accessor;
      p = myClass.findProperty("v4");
      assertNotNull(p);
      assertEquals("otherworldly", p.getDoc());
      PyTargetExpression site = p.getDefinitionSite();
      assertEquals("otherworldly", site.getText());

      accessor = p.getGetter();
      assertFalse(accessor.isDefined());

      accessor = p.getSetter();
      assertTrue(accessor.isDefined());
      assertNull(accessor.value());

      accessor = p.getDeleter();
      assertTrue(accessor.isDefined());
      assertNull(accessor.value());
    }
    */

  }

  public static class PyDecoratedPropertyTest extends PyPropertyTest {
    public PyDecoratedPropertyTest() {
      super();
    }

    @Override
    String getFileName() {
      return "Decorated.py";
    }

    @Override
    protected void setUp() throws Exception {
      super.setUp();
      myLanguageLevel = LanguageLevel.PYTHON26;
    }

    public void testW1() throws Exception {
      Property p;
      Maybe<PyFunction> accessor;
      p = myClass.findProperty("w1");
      assertNotNull(p);
      assertNull(p.getDoc());
      assertNull(p.getDefinitionSite());

      accessor = p.getGetter();
      assertTrue(accessor.isDefined());
      assertNotNull(accessor.value());
      assertEquals("w1", accessor.value().getName());

      accessor = p.getSetter();
      assertTrue(accessor.isDefined());
      assertNotNull(accessor.value());
      assertEquals("w1", accessor.value().getName());

      accessor = p.getDeleter();
      assertTrue(accessor.isDefined());
      assertNotNull(accessor.value());
      assertEquals("w1", accessor.value().getName());
    }

    public void testW2() throws Exception {
      Property p;
      Maybe<PyFunction> accessor;
      p = myClass.findProperty("w2");
      assertNotNull(p);
      assertNull(p.getDoc());
      assertNull(p.getDefinitionSite());

      accessor = p.getGetter();
      assertTrue(accessor.isDefined());
      assertNotNull(accessor.value());
      assertEquals("getter", accessor.value().getName());
      assertEquals("doc of w2", accessor.value().getDocStringExpression().getStringValue());

      accessor = p.getSetter();
      assertTrue(accessor.isDefined());
      assertNull(accessor.value());

      accessor = p.getDeleter();
      assertTrue(accessor.isDefined());
      assertNull(accessor.value());
    }

  }
}


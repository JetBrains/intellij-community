package com.jetbrains.python.packaging;

import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * @author Ilya.Kazakevich
 */
public class PyPackageTest extends TestCase {

  // http://legacy.python.org/dev/peps/pep-0386/
  public void testIsAtLeastVersionNormal() {
    final PyPackage pyPackage = new PyPackage("somePackage", "1.2.3.4", null, Collections.emptyList());
    assertTrue("Failed to check normal version", pyPackage.matches(createRequirement("somePackage>=1.2")));
    assertTrue("Failed to check normal version", pyPackage.matches(createRequirement("somePackage>=1.2.3")));
    assertTrue("Failed to check normal version", pyPackage.matches(createRequirement("somePackage>=1")));
    assertTrue("Failed to check normal version", pyPackage.matches(createRequirement("somePackage>=1.2.3.4")));

    assertFalse("Failed to check normal version", pyPackage.matches(createRequirement("somePackage>=1.2.3.4.5")));
    assertFalse("Failed to check normal version", pyPackage.matches(createRequirement("somePackage>=2")));
    assertFalse("Failed to check normal version", pyPackage.matches(createRequirement("somePackage>=2.2")));
    assertFalse("Failed to check normal version", pyPackage.matches(createRequirement("somePackage>=1.9.1")));
    assertFalse("Failed to check normal version", pyPackage.matches(createRequirement("somePackage>=1.2.3.5")));
    assertFalse("Failed to check normal version", pyPackage.matches(createRequirement("PackageFoo>=1.2.3.4")));
  }


  public void testIsAtLeastVersionBeta() {
    final PyPackage pyPackage = new PyPackage("somePackage", "0.5a3", null, Collections.emptyList());
    assertTrue("Failed to check alpha version", pyPackage.matches(createRequirement("somePackage>=0.4")));
    assertTrue("Failed to check alpha version", pyPackage.matches(createRequirement("somePackage<=0.5")));
    assertTrue("Failed to check alpha version", pyPackage.matches(createRequirement("somePackage>=0.5a")));

    assertFalse("Failed to check alpha version", pyPackage.matches(createRequirement("somePackage>=0.6")));
    assertFalse("Failed to check alpha version", pyPackage.matches(createRequirement("somePackage>=0.5.1")));
    assertFalse("Failed to check alpha version", pyPackage.matches(createRequirement("somePackage>=1")));
  }

  @NotNull
  private static PyRequirement createRequirement(@NotNull String options) {
    final PyRequirement requirement = PyPackageUtil.fix(PyRequirement.fromLine(options));
    assertNotNull(requirement);
    return requirement;
  }
}

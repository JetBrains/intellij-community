/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python;

import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyRequirement;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author vlan
 */
public class PyRequirementTest extends PyTestCase {
  public void testParseRequirement() {
    assertEquals(new PyRequirement("Django"), PyRequirement.fromString("Django"));
    assertEquals(new PyRequirement("django"), PyRequirement.fromString("django"));
    assertEquals(new PyRequirement("Django", "1.3.1"), PyRequirement.fromString("Django==1.3.1"));
    assertEquals(new PyRequirement("Django", list(new PyRequirement.VersionSpec(PyRequirement.Relation.LT, "1.4"))),
                 PyRequirement.fromString("   Django       <   1.4   "));
    assertEquals(new PyRequirement("Django", list(new PyRequirement.VersionSpec(PyRequirement.Relation.LT, "1.4"),
                                                  new PyRequirement.VersionSpec(PyRequirement.Relation.GTE, "1.3.1"))),
                 PyRequirement.fromString("   Django       <   1.4 ,     >= 1.3.1   "));
    assertEquals(null, PyRequirement.fromString("-e lib/django"));
    assertEquals(new PyRequirement("django", null, "svn+http://code.djangoproject.com/svn/django/trunk@17672", true),
                 PyRequirement.fromString("-e svn+http://code.djangoproject.com/svn/django/trunk@17672#egg=django"));
    assertEquals(new PyRequirement("django-haystack", "dev", "git://github.com/toastdriven/django-haystack.git@4fb267623b58", true),
                 PyRequirement.fromString("-e git://github.com/toastdriven/django-haystack.git@4fb267623b58#egg=django_haystack-dev"));
    assertEquals(list(new PyRequirement("django", "1.3"), new PyRequirement("foo")),
                 PyRequirement.parse("# This is a comment\n" +
                                     "django==1.3\n" +
                                     "--index-url http://example.com/\n" +
                                     "foo\n"));
  }

  // PY-6355
  public void testTrailingZeroesInVersion() {
    final PyRequirement req080 = PyRequirement.fromString("foo==0.8.0");
    final PyPackage pack08 = new PyPackage("foo", "0.8", null, Collections.<PyRequirement>emptyList());
    assertNotNull(req080);
    assertTrue(req080.match(list(pack08)) != null);
  }

  // PY-6438
  public void testUnderscoreMatchesDash() {
    final PyRequirement req = PyRequirement.fromString("pyramid_zcml");
    final PyPackage pkg = new PyPackage("pyramid-zcml", "0.1", null, Collections.<PyRequirement>emptyList());
    assertNotNull(req);
    assertTrue(req.match(list(pkg)) != null);
  }

  // PY-6328
  public void testNotEditableGitRequirement() {
    assertEquals(new PyRequirement("django-haystack", "dev", "git://github.com/toastdriven/django-haystack.git@4fb267623b58", false),
                 PyRequirement.fromString("git://github.com/toastdriven/django-haystack.git@4fb267623b58#egg=django_haystack-dev"));
  }

  // PY-7034
  public void testMinusInRequirementEggName() {
    assertEquals(new PyRequirement("django-haystack", null, "git://github.com/toastdriven/django-haystack.git", true),
                 PyRequirement.fromString("-e git://github.com/toastdriven/django-haystack.git#egg=django-haystack"));
    assertEquals(new PyRequirement("django-haystack", "dev", "git://github.com/toastdriven/django-haystack.git", true),
                 PyRequirement.fromString("-e git://github.com/toastdriven/django-haystack.git#egg=django-haystack-dev"));
  }

  // PY-7583
  public void testGitRequirementWithoutEggName() {
    assertEquals(new PyRequirement("django-haystack", null, "git://github.com/toastdriven/django-haystack.git@4fb267623b58", false),
                 PyRequirement.fromString("git://github.com/toastdriven/django-haystack.git@4fb267623b58"));
    assertEquals(new PyRequirement("django-haystack", null, "git+git://github.com/toastdriven/django-haystack.git@4fb267623b58", false),
                 PyRequirement.fromString("git+git://github.com/toastdriven/django-haystack.git@4fb267623b58"));
    assertEquals(new PyRequirement("django-haystack", null, "git+git://github.com/toastdriven/django-haystack.git@4fb267623b58", true),
                 PyRequirement.fromString("-e git+git://github.com/toastdriven/django-haystack.git@4fb267623b58"));
    assertEquals(new PyRequirement("django-haystack", null, "git+git://github.com/toastdriven/django-haystack.git", false),
                 PyRequirement.fromString("git+git://github.com/toastdriven/django-haystack.git"));
    assertEquals(new PyRequirement("django-piston", null, "hg+ssh://hg@bitbucket.org/jespern/django-piston", false),
                 PyRequirement.fromString("hg+ssh://hg@bitbucket.org/jespern/django-piston"));
    assertEquals(new PyRequirement("django-piston", null, "hg+ssh://hg@bitbucket.org/jespern/django-piston/", false),
                 PyRequirement.fromString("hg+ssh://hg@bitbucket.org/jespern/django-piston/"));
  }

  // PY-8623
  public void testGitRevisionWithSlash() {
    assertEquals(new PyRequirement("django", null, "git+git://github.com/django/django.git@stable/1.5.x", false),
                 PyRequirement.fromString("git+git://github.com/django/django.git@stable/1.5.x"));
  }

  // PY-18543
  public void testRecursiveRequirement() {
    final VirtualFile requirementsFile = getVirtualFileByName(getTestDataPath() + "/requirement/recursive/requirements.txt");
    assertNotNull(requirementsFile);

    assertEquals(list(new PyRequirement("bitly_api"), new PyRequirement("numpy")),
                 PyRequirement.parse(requirementsFile));
  }

  private static <T> List<T> list(T... xs) {
    return Arrays.asList(xs);
  }
}

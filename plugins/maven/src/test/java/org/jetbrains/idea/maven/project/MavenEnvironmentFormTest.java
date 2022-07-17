// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.command.impl.DummyProject;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.ui.TextFieldWithHistory;
import com.intellij.util.Consumer;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.idea.maven.server.MavenDistributionsCache;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.lang.reflect.Field;
import java.util.Collections;

@RunWith(JUnit4.class)
public class MavenEnvironmentFormTest extends UsefulTestCase {

  @Test
  public void shouldNotShowDuplicatedBundledMavenHome() {
    MavenGeneralPanel panel = new MavenGeneralPanel();
    assertThat(panel,
               t -> assertContainsElements(t.getHistory(),
                                       Collections.singleton(MavenServerManager.BUNDLED_MAVEN_3)));
    assertThat(panel,
               t -> assertDoesntContain(t.getHistory(),
                                        MavenDistributionsCache.resolveEmbeddedMavenHome().getMavenHome().toAbsolutePath().toString()));
  }

  @Test
  public void shouldSetBundledMavenIfSetAbsolutePath() {
    MavenGeneralSettings settings = new MavenGeneralSettings();
    MavenGeneralPanel panel = new MavenGeneralPanel();
    settings.setMavenHome(MavenServerManager.BUNDLED_MAVEN_3);
    panel.initializeFormData(settings,  DummyProject.getInstance());
    assertThat(panel,
               t -> assertEquals("Absolute path to bundled maven should resolve to bundle", MavenServerManager.BUNDLED_MAVEN_3,
                                 t.getText()));
  }

  @Test
  public void shouldNotSetBundledMavenIfAnotherMavenSet() {
    MavenGeneralSettings settings = new MavenGeneralSettings();
    MavenGeneralPanel panel = new MavenGeneralPanel();
    settings.setMavenHome("/path/to/maven/home");
    panel.initializeFormData(settings, DummyProject.getInstance());
    assertThat(panel,
               t -> assertEquals("/path/to/maven/home", t.getText()));
  }

  private void assertThat(MavenGeneralPanel configurable, Consumer<TextFieldWithHistory> checker) {
    MavenEnvironmentForm form = getValue(MavenEnvironmentForm.class, configurable, "mavenPathsForm");
    TextFieldWithHistory mavenHomeField = getValue(TextFieldWithHistory.class, form, "mavenHomeField");
    checker.consume(mavenHomeField);
  }

  protected <T> T getValue(Class<T> fieldClass, Object object, String name) {
    try {
      Field field = ReflectionUtil.findAssignableField(object.getClass(), fieldClass, name);
      return ReflectionUtil.getFieldValue(field, object);
    }
    catch (NoSuchFieldException e) {
      fail("No such field " + name + " in " + object);
      return null;
    }
  }
}
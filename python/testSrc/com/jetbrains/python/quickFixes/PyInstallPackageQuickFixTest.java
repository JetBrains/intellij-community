// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.quickFixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyQuickFixTestCase;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class PyInstallPackageQuickFixTest extends PyQuickFixTestCase {

  public void testImport1() {
    doTest("import xyz", List.of("xyz"));
  }

  public void testImport2() {
    doTest("import xyz as x, uvw as u", List.of("xyz", "uvw"));
  }

  public void testFromImport1() {
    doTest("from rich import xyz, uvw as u", List.of("rich"));
  }

  public void testFromImport2() {
    doTest("from rich import (xyz,\nuvw as u)", List.of("rich"));
  }

  public void testFromImport3() {
    doTest("from rich import *", List.of("rich"));
  }

  public void testFromImport4() {
    doTest("from . import xyz", List.of());
  }

  private void doTest(@NotNull String text, @NotNull Collection<@NotNull String> expectedInstallPackageNames) {
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    String hint = getInstallPackageHint("");

    List<String> actual = StreamEx
      .of(myFixture.getAllQuickFixes())
      .map(IntentionAction::getText)
      .filter(t -> t.startsWith(hint))
      .sorted()
      .toList();

    List<String> expected = StreamEx
      .of(expectedInstallPackageNames)
      .map(PyInstallPackageQuickFixTest::getInstallPackageHint)
      .sorted()
      .toList();

    assertSameElements(actual, expected);
  }

  @Contract(pure = true)
  private static @NotNull String getInstallPackageHint(@NotNull String packageName) {
    return PyBundle.message("python.unresolved.reference.inspection.install.package", packageName);
  }
}

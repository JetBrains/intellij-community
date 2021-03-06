// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.lomboktest;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class LombokNonNullManagerTest extends LightJavaCodeInsightFixtureTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return LombokTestUtil.LOMBOK_DESCRIPTOR;
  }

  public void testTypeAnnotationNullabilityOnStubs() {
    List<String> notNulls = NullableNotNullManager.getInstance(getProject()).getNotNulls();
    assertTrue(notNulls.contains(LombokClassNames.NON_NULL));

    PsiClass clazz = myFixture.addClass("import lombok.NonNull;\n" +
                                        "public class NonNullTest {\n" +
                                        "    @NonNull\n" +
                                        "    private String test(@NonNull Integer param) {\n" +
                                        "        return String.valueOf(param.hashCode());\n" +
                                        "    }" +
                                        "}");
    assertEquals(Nullability.NOT_NULL, DfaPsiUtil.getTypeNullability(clazz.getMethods()[0].getReturnType()));
    assertEquals(Nullability.NOT_NULL, DfaPsiUtil.getTypeNullability(clazz.getMethods()[0].getParameterList().getParameter(0).getType()));
  }
}

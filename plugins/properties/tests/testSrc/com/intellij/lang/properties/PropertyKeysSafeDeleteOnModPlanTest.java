// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.model.SymbolPointer;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import com.intellij.refactoring.modplan.ModPlanRequest;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteRefactoring;
import com.intellij.refactoring.modplan.safeDelete.impl.ModPlanSafeDeleteFlow;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

/**
 * The safe delete of a property key on the ModPlan engine, through the bridge of the legacy delegates.
 */
public class PropertyKeysSafeDeleteOnModPlanTest extends BasePlatformTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Registry.get("refactoring.safeDelete.modPlan").setValue(true, getTestRootDisposable());
  }

  public void testKeyIsSupported() {
    PsiElement key = keyOf(myFixture.configureByText("Messages.properties", "first=1\nsecond=2\n"), "first");

    assertTrue(ReadAction.computeBlocking(
      () -> ModPlanSafeDeleteRefactoring.getInstance().isSupported(new ModPlanRequest(getProject(), List.of(SymbolPointer.of(key)), ModPlanOptionValues.EMPTY))));
  }

  /** As in the legacy flow, the key goes from every file of the bundle without a question. */
  public void testKeyIsDeletedInEveryFileOfTheBundle() {
    PsiFile defaultFile = myFixture.configureByText("Messages.properties", "first=1\nsecond=2\n");
    PsiFile germanFile = myFixture.addFileToProject("Messages_de.properties", "first=eins\nsecond=zwei\n");

    assertTrue(deleteOnModPlan(keyOf(defaultFile, "first")));

    assertFalse(defaultFile.getText(), defaultFile.getText().contains("first"));
    assertTrue(defaultFile.getText(), defaultFile.getText().contains("second=2"));
    assertFalse(germanFile.getText(), germanFile.getText().contains("first"));
    assertTrue(germanFile.getText(), germanFile.getText().contains("second=zwei"));
  }

  private static PsiElement keyOf(PsiFile file, String key) {
    IProperty property = ((PropertiesFile)file).findPropertyByKey(key);
    assertNotNull(key, property);
    return property.getPsiElement();
  }

  /** @return whether the flow on the ModPlan engine handled the deletion, which is false if the legacy flow had to run */
  private boolean deleteOnModPlan(PsiElement element) {
    return ModPlanSafeDeleteFlow.INSTANCE.start(getProject(), new PsiElement[]{element}, false, true, null, null);
  }
}

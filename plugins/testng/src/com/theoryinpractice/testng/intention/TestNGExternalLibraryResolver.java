// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.theoryinpractice.testng.intention;

import com.intellij.codeInsight.daemon.quickFix.ExternalLibraryResolver;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public final class TestNGExternalLibraryResolver extends ExternalLibraryResolver {
  private static final Set<String> TEST_NG_ANNOTATIONS = Set.of(
    "Test", "BeforeClass", "BeforeGroups", "BeforeMethod", "BeforeSuite", "BeforeTest", "AfterClass", "AfterGroups", "AfterMethod",
    "AfterSuite", "AfterTest", "Configuration"
  );
  public static final ExternalLibraryDescriptor TESTNG_DESCRIPTOR = new ExternalLibraryDescriptor("org.testng", "testng", null, null, "7.1.0");

  @Override
  public @Nullable ExternalClassResolveResult resolveClass(@NotNull String shortClassName, @NotNull ThreeState isAnnotation, @NotNull Module contextModule) {
    if (TEST_NG_ANNOTATIONS.contains(shortClassName)) {
      return new ExternalClassResolveResult("org.testng.annotations." + shortClassName, TESTNG_DESCRIPTOR);
    }
    return null;
  }
}

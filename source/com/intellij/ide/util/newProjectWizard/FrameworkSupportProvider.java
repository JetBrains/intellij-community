/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.util.newProjectWizard;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.ModuleType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class FrameworkSupportProvider {
  public static final ExtensionPointName<FrameworkSupportProvider> EXTENSION_POINT = ExtensionPointName.create("com.intellij.frameworkSupport");
  private String myId;
  private String myTitle;

  protected FrameworkSupportProvider(final @NonNls @NotNull String id, final @NotNull String title) {
    myId = id;
    myTitle = title;
  }

  @NotNull
  public abstract FrameworkSupportConfigurable createConfigurable();

  @Nullable
  public String getUnderlyingFrameworkId() {
    return null;
  }

  public String[] getPrecedingFrameworkProviderIds() {
    return new String[0];
  }

  public String getTitle() {
    return myTitle;
  }

  public boolean isEnabledForModuleType(@NotNull ModuleType moduleType) {
    return moduleType == ModuleType.JAVA;
  }

  public final String getId() {
    return myId;
  }
}

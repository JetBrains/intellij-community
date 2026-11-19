// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.java.facet;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.facet.PythonFacetDefaults;
import com.jetbrains.python.parser.icons.PythonParserIcons;
import com.jetbrains.python.sdk.internal.PySdkNamesKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

@ApiStatus.Internal
final class JavaPythonFacetType extends FacetType<JavaPythonFacet, JavaPythonFacetConfiguration> {

  private static final @NonNls String ID = PySdkNamesKt.PYTHON_FACET_ID;

  public static JavaPythonFacetType getInstance() {
    return findInstance(JavaPythonFacetType.class);
  }

  public JavaPythonFacetType() {
    super(JavaPythonFacet.ID, ID, PyBundle.message("python.facet.name"));
  }

  @Override
  public JavaPythonFacetConfiguration createDefaultConfiguration() {
    JavaPythonFacetConfiguration result = new JavaPythonFacetConfiguration();
    result.setSdk(PythonFacetDefaults.defaultPythonFacetSdk());
    return result;
  }

  @Override
  public JavaPythonFacet createFacet(@NotNull Module module, String name, @NotNull JavaPythonFacetConfiguration configuration, @Nullable Facet underlyingFacet) {
    return new JavaPythonFacet(this, module, name, configuration, underlyingFacet);
  }

  @Override
  public boolean isSuitableModuleType(ModuleType moduleType) {
    return !(moduleType instanceof PythonModuleType);
  }

  @Override
  public Icon getIcon() {
    return PythonParserIcons.PythonFile;
  }
}

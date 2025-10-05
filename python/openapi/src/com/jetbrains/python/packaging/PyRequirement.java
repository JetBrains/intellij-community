// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.packaging.requirement.PyRequirementVersionSpec;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;


/**
 * @see <a href="https://pip.pypa.io/en/stable/reference/pip_install/"><code>pip install</code> documentation</a>
 * @see <a href="https://www.python.org/dev/peps/pep-0508/">PEP-508</a>
 * @see <a href="https://www.python.org/dev/peps/pep-0440/">PEP-440</a>
 */
public interface PyRequirement {
  @NotNull
  String getName();

  @NotNull
  PyPackageName getPackageName();

  @NotNull
  List<PyRequirementVersionSpec> getVersionSpecs();

  /**
   * @return list of options to pass to <code>pip install</code>.
   * <i>
   * Note:
   * if list has more than one element it means that
   * <code>--src</code>, <code>-e</code>, <code>--editable</code>, <code>--global-option</code> or <code>--install-option</code>
   * options are used
   * </i>.
   */
  @NotNull
  List<String> getInstallOptions();

  @NotNull
  String getExtras();

  /**
   * @param packages packages to match
   * @return first package that satisfies this requirement or null.
   */
  @Nullable
  PyPackage match(@NotNull Collection<PyPackage> packages);

  boolean match(@NotNull PyPackage packageName);

  default boolean isEditable() {
    if (getInstallOptions().isEmpty()) return false;
    String firstOption = getInstallOptions().get(0);
    return "-e".equals(firstOption) || "--editable".equals(firstOption);
  }

  /**
   * @return concatenated representation of name, extras and version specs, so it could be easily displayed.
   */
  default @NotNull @NlsSafe String getPresentableText() {
    return getPresentableTextWithoutVersion() + getExtras() + StringUtil.join(getVersionSpecs(), PyRequirementVersionSpec::getPresentableText, ",");
  }

  @NotNull @NlsSafe String getPresentableTextWithoutVersion();

  @ApiStatus.Internal
  @NotNull PyRequirement withVersionSpecs(@NotNull List<PyRequirementVersionSpec> spec);
}

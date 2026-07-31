// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.packaging.requirement.PyRequirementEnvMarker;
import com.jetbrains.python.packaging.requirement.PyRequirementEnvMarkerType;
import com.jetbrains.python.packaging.requirement.PyRequirementVersionSpec;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;


/**
 * @see <a href="https://pip.pypa.io/en/stable/reference/pip_install/"><code>pip install</code> documentation</a>
 * @see <a href="https://www.python.org/dev/peps/pep-0508/">PEP-508</a>
 * @see <a href="https://www.python.org/dev/peps/pep-0440/">PEP-440</a>
 */
public interface PyRequirement {
  @NotNull
  @NlsSafe
  String getName();

  @NotNull
  PyPackageName getPackageName();

  @NotNull
  List<PyRequirementVersionSpec> getVersionSpecs();

  /**
   * @return the URL reference to the package
   * <i>
   * Note: this is only present in requirements that contain a URL reference, either directly to an installable
   * archive or a VCS repository, such as
   * <code>mypackage @ https://example.org/mypackage-1.0.0-py3-any-none.whl</code>
   * or <code>mypackage @ git+https://example.org/mypackage.git</code>
   * </i>
   */
  @Nullable
  String getUrlReference();

  /**
   * @return list of options to pass to <code>pip install</code>.
   * <i>
   * Note: the list always contains at least one element (the name of the package),
   * </i>.
   */
  @NotNull
  List<String> getInstallOptions();

  /**
   * @return the environment marker for this requirement, or null if there is no marker.
   * <i>
   * Note: multiple environment markers are represented with specialized collection classes
   * implementing the {@link PyRequirementEnvMarker} interface.
   * </i>
   */
  @Nullable
  PyRequirementEnvMarker getEnvironmentMarker();

  @NotNull
  String getExtras();

  /**
   * @param packages packages to match
   * @return first package that satisfies this requirement or null.
   */
  @Nullable
  PyPackage match(@NotNull Collection<PyPackage> packages);

  boolean match(@NotNull PyPackage packageName);

  /**
   * Checks if the environment markers in this requirement match the current system and interpreter.
   *
   * @param platformData the platform data, collected from the interpreter, to check against.
   * @return <code>true</code> if this requirement applies to the given platform data.
   */
  default boolean appliesTo(@NotNull Map<PyRequirementEnvMarkerType, String> platformData) {
    PyRequirementEnvMarker marker = getEnvironmentMarker();
    return marker == null || marker.matches(platformData);
  }

  default boolean isEditable() {
    if (getInstallOptions().isEmpty()) return false;
    String firstOption = getInstallOptions().get(0);
    return "-e".equals(firstOption) || "--editable".equals(firstOption);
  }

  /**
   * @return concatenated representation of name, extras and version specs, so it could be easily displayed.
   */
  default @NotNull @NlsSafe String getPresentableText() {
    String extras = getExtras();
    return getPresentableTextWithoutVersion() +
           (extras.isEmpty() ? "" : "[" + extras + "]") +
           StringUtil.join(getVersionSpecs(), PyRequirementVersionSpec::getPresentableText, ",");
  }

  @NotNull
  @NlsSafe
  String getPresentableTextWithoutVersion();

  @ApiStatus.Internal
  @NotNull PyRequirement withVersionSpecs(@NotNull List<PyRequirementVersionSpec> spec);
}

// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.jetbrains.python.packaging;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.packaging.requirement.PyRequirementVersionSpec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;


/**
 * @see <a href="https://pip.pypa.io/en/stable/reference/pip_install/"><code>pip install</code> documentation</a>
 * @see <a href="https://www.python.org/dev/peps/pep-0508/">PEP-508</a>
 * @see <a href="https://www.python.org/dev/peps/pep-0440/">PEP-440</a>
 * @see PyPackageManager#parseRequirement(String)
 * @see PyPackageManager#parseRequirements(String)
 * @see PyPackageManager#parseRequirements(VirtualFile)
 */
public interface PyRequirement {

  @NotNull
  String getName();

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
  PyPackage match(@NotNull Collection<? extends PyPackage> packages);

  /**
   * @return concatenated representation of name, extras and version specs so it could be easily displayed.
   */
  @NotNull
  default String getPresentableText() {
    return getName() + getExtras() + StringUtil.join(getVersionSpecs(), PyRequirementVersionSpec::getPresentableText, ",");
  }
}

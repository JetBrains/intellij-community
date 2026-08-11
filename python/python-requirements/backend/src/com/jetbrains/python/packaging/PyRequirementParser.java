// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @see <a href="https://pip.pypa.io/en/stable/reference/pip_install/"><code>pip install</code> documentation</a>
 * @see <a href="https://www.python.org/dev/peps/pep-0508/">PEP-508</a>
 * @see <a href="https://www.python.org/dev/peps/pep-0440/">PEP-440</a>
 * @see PyRequirement
 * @see PyPackageVersionNormalizer
 * @deprecated Use {@link com.intellij.python.requirements.parser.PyRequirementParser} instead.
 */
@Deprecated(forRemoval = true)
public final class PyRequirementParser {
  public static @Nullable PyRequirement fromLine(@NotNull String line) {
    return com.intellij.python.requirements.parser.PyRequirementParser.fromLine(line);
  }

  public static @NotNull List<PyRequirement> fromText(@NotNull String text) {
    return com.intellij.python.requirements.parser.PyRequirementParser.fromText(text);
  }

  public static @NotNull List<PyRequirement> fromFile(@NotNull VirtualFile file) {
    return com.intellij.python.requirements.parser.PyRequirementParser.fromFile(file);
  }
}

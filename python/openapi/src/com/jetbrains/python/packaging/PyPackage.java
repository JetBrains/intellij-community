/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.packaging;

import com.google.common.base.Preconditions;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.webcore.packaging.InstalledPackage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author vlan
 */
public class PyPackage extends InstalledPackage {
  private static final Pattern DIGITS = Pattern.compile("^([0-9]+).*$");
  private static final Pattern VERSION_PART_SEPARATOR = Pattern.compile("\\.");
  private final String myLocation;
  private final List<PyRequirement> myRequirements;

  public PyPackage(@NotNull String name, @NotNull String version, @Nullable String location, @NotNull List<PyRequirement> requirements) {
    super(name, version);
    myLocation = location;
    myRequirements = requirements;
  }

  @NotNull
  public List<PyRequirement> getRequirements() {
    return myRequirements;
  }


  /**
   * Checks package version (it should be in [PEP-386] format), but only digits (not letters) are supported.
   *
   * @param expectedVersionParts Digits from major to minor: 1.2.42 version should be checked as "1,2,42".
   * @return true if package version is greater or equals.
   */
  public boolean isVersionAtLeast(@NotNull final int... expectedVersionParts) {
    final String version = getVersion();
    if (version == null) {
      throw new IllegalStateException("Package has no version");
    }
    Preconditions.checkArgument(expectedVersionParts.length > 0, "At least one version part should be provided");

    final String[] versionParts = VERSION_PART_SEPARATOR.split(version);

    for (int i = 0; i < expectedVersionParts.length; i++) {
      if ((versionParts.length - 1) < i) {
        return false;
      }
      final Matcher matcher = DIGITS.matcher(versionParts[i]);
      if (!(matcher.find() && (matcher.groupCount() == 1))) {
        throw new IllegalArgumentException("Can't parse " + versionParts[i]);
      }
      final int versionPart = Integer.parseInt(matcher.group(1));
      if (versionPart < expectedVersionParts[i]) {
        return false;
      }
    }
    return true;
  }


  @Nullable
  public String getLocation() {
    return myLocation;
  }

  public boolean isInstalled() {
    return myLocation != null;
  }

  @Nullable
  @Override
  public String getTooltipText() {
    return FileUtil.getLocationRelativeToUserHome(myLocation);
  }
}

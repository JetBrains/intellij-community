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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.webcore.packaging.InstalledPackage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author vlan
 */
public class PyPackage extends InstalledPackage {
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

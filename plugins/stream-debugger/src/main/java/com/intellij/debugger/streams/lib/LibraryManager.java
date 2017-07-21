/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.debugger.streams.lib;

import com.intellij.debugger.streams.wrapper.StreamCall;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vitaliy.Bibaev
 */
public interface LibraryManager extends ProjectComponent {
  static LibraryManager getInstance(@NotNull Project project) {
    return project.getComponent(LibraryManager.class);
  }

  boolean isPackageSupported(@NotNull String packageName);

  @NotNull
  LibrarySupport getLibraryByPackage(@NotNull String packageName);

  @NotNull
  default LibrarySupport getLibrary(@NotNull StreamCall call) {
    return getLibraryByPackage(call.getPackageName());
  }
}
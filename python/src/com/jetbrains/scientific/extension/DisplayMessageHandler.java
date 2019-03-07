// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.scientific.extension;

import com.google.gson.JsonObject;
import com.intellij.openapi.project.Project;
import com.jetbrains.scientific.figure.Figure;
import org.jetbrains.annotations.NotNull;

public interface DisplayMessageHandler {
  String getApplicableMessageType();

  Figure createFigure(@NotNull JsonObject dataObject, @NotNull Project project);
}

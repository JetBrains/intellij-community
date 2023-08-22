// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.youtrack.model;

import com.google.gson.annotations.SerializedName;

/**
 * @noinspection unused
 */
public class YouTrackErrorInfo {
  private String error;
  @SerializedName("error_description")
  private String errorDescription;

  public String getError() {
    return error;
  }

  public String getErrorDescription() {
    return errorDescription;
  }
}

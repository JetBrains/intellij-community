// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MavenFragmentData implements Serializable {
  private final ArrayList<String> myFocused;

  public MavenFragmentData(List<String> focused) {
    myFocused = new ArrayList<>(focused);
  }

  public List<String> getFocused() {
    return Collections.unmodifiableList(myFocused);
  }
}

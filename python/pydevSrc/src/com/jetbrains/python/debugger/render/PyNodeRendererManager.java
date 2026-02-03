// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.render;

import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.List;

public final class PyNodeRendererManager {

  private static final PyNodeRendererManager INSTANCE = new PyNodeRendererManager();

  private final List<PyNodeRenderer> myAvailableRenderers = new ArrayList<>();

  private PyNodeRendererManager() {
    myAvailableRenderers.add(new BinaryRenderer());
    myAvailableRenderers.add(new DecimalRenderer());
    myAvailableRenderers.add(new HexRenderer());
  }

  @Contract(pure = true)
  public static PyNodeRendererManager getInstance() {
    return INSTANCE;
  }

  public List<PyNodeRenderer> getAvailableRenderers() {
    return new ArrayList<>(myAvailableRenderers);
  }
}

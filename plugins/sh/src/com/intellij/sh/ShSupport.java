// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh;

import com.intellij.openapi.components.ServiceManager;

public interface ShSupport {
  static ShSupport getInstance() { return ServiceManager.getService(ShSupport.class); }

  boolean isExternalFormatterEnabled();

  boolean isRenameEnabled();

  class Impl implements ShSupport {
    @Override
    public boolean isExternalFormatterEnabled() { return true; }

    @Override
    public boolean isRenameEnabled() { return true; }
  }
}
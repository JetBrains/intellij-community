package com.intellij.ide.browsers;

import com.intellij.openapi.options.Configurable;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

/**
 * @author nik
 */
public abstract class BrowserSpecificSettings {

  public abstract Configurable createConfigurable();

  @NotNull @NonNls
  public String[] getAdditionalParameters() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }
}

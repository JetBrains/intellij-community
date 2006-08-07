package com.intellij.util.lang;

import org.jetbrains.annotations.Nullable;
import sun.misc.Resource;

import java.net.URL;

abstract class Loader {
  private final URL myURL;

  protected Loader(URL url) {
    myURL = url;
  }


  protected URL getBaseURL() {
    return myURL;
  }

  @Nullable
  abstract Resource getResource(final String name, boolean flag);
}

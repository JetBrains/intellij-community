package com.intellij.psi.impl.source.jsp.tagLibrary;

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

  abstract Resource getResource(final String name, boolean flag);
}

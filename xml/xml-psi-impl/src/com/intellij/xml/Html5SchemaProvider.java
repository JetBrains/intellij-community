// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;

import java.net.URL;
import java.util.function.Supplier;

public abstract class Html5SchemaProvider {
  private static final Logger LOG = Logger.getInstance(Html5SchemaProvider.class);
  private static final Supplier<String>
    HTML5_SCHEMA_LOCATION = new SynchronizedClearableLazy<>(() -> loadLocation(getInstance().getHtmlSchemaLocation(), "HTML5_SCHEMA"));
  private static final Supplier<String> XHTML5_SCHEMA_LOCATION = new SynchronizedClearableLazy<>(() -> loadLocation(getInstance().getXhtmlSchemaLocation(), "XHTML5_SCHEMA"));
  private static final Supplier<String> CHARS_DTD_LOCATION = new SynchronizedClearableLazy<>(() -> loadLocation(getInstance().getCharsLocation(), "CHARS_DTD"));

  private static String loadLocation(URL url, String id) {
    String location = VfsUtilCore.urlToPath(VfsUtilCore.fixURLforIDEA(
      URLUtil.unescapePercentSequences(url.toExternalForm())));
    LOG.info(id + "_LOCATION = " + location);
    return location;
  }

  public static String getHtml5SchemaLocation() {
    return HTML5_SCHEMA_LOCATION.get();
  }

  public static String getXhtml5SchemaLocation() {
    return XHTML5_SCHEMA_LOCATION.get();
  }

  public static String getCharsDtdLocation() {
    return CHARS_DTD_LOCATION.get();
  }

  private static @NotNull Html5SchemaProvider getInstance() {
    return ApplicationManager.getApplication().getService(Html5SchemaProvider.class);
  }

  public abstract @NotNull URL getHtmlSchemaLocation();

  public abstract @NotNull URL getXhtmlSchemaLocation();

  public abstract @NotNull URL getCharsLocation();
}

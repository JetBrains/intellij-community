// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;

import java.net.URL;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class Html5SchemaProvider {
  private static final Logger LOG = Logger.getInstance(Html5SchemaProvider.class);
  private static final NotNullLazyValue<String> HTML5_SCHEMA_LOCATION = NotNullLazyValue.atomicLazy(() -> loadLocation(getInstance().getHtmlSchemaLocation(), "HTML5_SCHEMA"));
  private static final NotNullLazyValue<String> XHTML5_SCHEMA_LOCATION = NotNullLazyValue.atomicLazy(() -> loadLocation(getInstance().getXhtmlSchemaLocation(), "XHTML5_SCHEMA"));
  private static final NotNullLazyValue<String> CHARS_DTD_LOCATION = NotNullLazyValue.atomicLazy(() -> loadLocation(getInstance().getCharsLocation(), "CHARS_DTD"));

  private static String loadLocation(URL url, String id) {
    String location = VfsUtilCore.urlToPath(VfsUtilCore.fixURLforIDEA(
      URLUtil.unescapePercentSequences(url.toExternalForm())));
    LOG.info(id + "_LOCATION = " + location);
    return location;
  }

  public static String getHtml5SchemaLocation() {
    return HTML5_SCHEMA_LOCATION.getValue();
  }

  public static String getXhtml5SchemaLocation() {
    return XHTML5_SCHEMA_LOCATION.getValue();
  }

  public static String getCharsDtdLocation() {
    return CHARS_DTD_LOCATION.getValue();
  }

  @NotNull
  private static Html5SchemaProvider getInstance() {
    return ApplicationManager.getApplication().getService(Html5SchemaProvider.class);
  }

  @NotNull
  public abstract URL getHtmlSchemaLocation();

  @NotNull
  public abstract URL getXhtmlSchemaLocation();

  @NotNull
  public abstract URL getCharsLocation();
}

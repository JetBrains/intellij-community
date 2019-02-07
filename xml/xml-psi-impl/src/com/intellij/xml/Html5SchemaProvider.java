// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml;

import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;

import java.net.URL;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class Html5SchemaProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xml.Html5SchemaProvider");

  public static final ExtensionPointName<Html5SchemaProvider> EP_NAME = ExtensionPointName.create("com.intellij.xml.html5SchemaProvider");

  private static String HTML5_SCHEMA_LOCATION;
  private static String XHTML5_SCHEMA_LOCATION;
  private static String CHARS_DTD_LOCATION;

  private static boolean ourInitialized;

  public static String getHtml5SchemaLocation() {
    ensureInitialized();
    return HTML5_SCHEMA_LOCATION;
  }

  public static String getXhtml5SchemaLocation() {
    ensureInitialized();
    return XHTML5_SCHEMA_LOCATION;
  }

  public static String getCharsDtdLocation() {
    ensureInitialized();
    return CHARS_DTD_LOCATION;
  }


  private synchronized static void ensureInitialized() {
    if (ourInitialized) return;
    ourInitialized = true;

    final List<Html5SchemaProvider> providers = EP_NAME.getExtensionList();
    final URL htmlSchemaLocationURL;
    final URL xhtmlSchemaLocationURL;
    final URL dtdCharsLocationURL;

    if (providers.size() > 1) {
      LOG.error("More than one HTML5 schema providers found: " + getClassesListString(providers));
    }

    if (providers.size() > 0) {
      htmlSchemaLocationURL = providers.get(0).getHtmlSchemaLocation();
      xhtmlSchemaLocationURL = providers.get(0).getXhtmlSchemaLocation();
      dtdCharsLocationURL = providers.get(0).getCharsLocation();
    }
    else {
      LOG.info("RelaxNG based schema for HTML5 is not supported. Old XSD schema will be used");
      htmlSchemaLocationURL = Html5SchemaProvider.class.getResource(ExternalResourceManagerEx.STANDARD_SCHEMAS + "html5/xhtml5.xsd");
      xhtmlSchemaLocationURL = htmlSchemaLocationURL;
      dtdCharsLocationURL = htmlSchemaLocationURL;
    }

    HTML5_SCHEMA_LOCATION = VfsUtilCore.urlToPath(VfsUtilCore.fixURLforIDEA(
      URLUtil.unescapePercentSequences(htmlSchemaLocationURL.toExternalForm())));
    LOG.info("HTML5_SCHEMA_LOCATION = " + getHtml5SchemaLocation());

    XHTML5_SCHEMA_LOCATION = VfsUtilCore.urlToPath(VfsUtilCore.fixURLforIDEA(
      URLUtil.unescapePercentSequences(xhtmlSchemaLocationURL.toExternalForm())));
    LOG.info("XHTML5_SCHEMA_LOCATION = " + getXhtml5SchemaLocation());

    CHARS_DTD_LOCATION = VfsUtilCore.urlToPath(VfsUtilCore.fixURLforIDEA(
      URLUtil.unescapePercentSequences(dtdCharsLocationURL.toExternalForm())));
    LOG.info("CHARS_DTD_LOCATION = " + getCharsDtdLocation());
  }

  @NotNull
  public abstract URL getHtmlSchemaLocation();

  @NotNull
  public abstract URL getXhtmlSchemaLocation();

  @NotNull
  public abstract URL getCharsLocation();

  static {
  }

  @NotNull
  private static <T> String getClassesListString(@NotNull List<T> a) {
    final StringBuilder builder = new StringBuilder();
    for (int i = 0, n = a.size(); i < n; i++) {
      T element = a.get(i);
      builder.append(element != null ? element.getClass().getName() : "NULL");
      if (i < n - 1) {
        builder.append(", ");
      }
    }
    return builder.toString();
  }
}

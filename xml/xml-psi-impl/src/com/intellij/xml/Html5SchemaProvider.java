package com.intellij.xml;

import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;

import java.net.URL;

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

    final Html5SchemaProvider[] providers = EP_NAME.getExtensions();
    final URL htmlSchemaLocationURL;
    final URL xhtmlSchemaLocationURL;
    final URL dtdCharsLocationURL;

    if (providers.length > 1) {
      LOG.error("More than one HTML5 schema providers found: " + getClassesListString(providers));
    }

    if (providers.length > 0) {
      htmlSchemaLocationURL = providers[0].getHtmlSchemaLocation();
      xhtmlSchemaLocationURL = providers[0].getXhtmlSchemaLocation();
      dtdCharsLocationURL = providers[0].getCharsLocation();
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

  private static <T> String getClassesListString(T[] a) {
    final StringBuilder builder = new StringBuilder();
    for (int i = 0, n = a.length; i < n; i++) {
      T element = a[i];
      builder.append(element != null ? element.getClass().getName() : "NULL");
      if (i < n - 1) {
        builder.append(", ");
      }
    }
    return builder.toString();
  }
}

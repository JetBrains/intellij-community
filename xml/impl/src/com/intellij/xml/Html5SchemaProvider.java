package com.intellij.xml;

import com.intellij.javaee.ExternalResourceManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import org.jetbrains.annotations.NotNull;

import java.net.URL;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class Html5SchemaProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xml.Html5SchemaProvider");

  private static final ExtensionPointName<Html5SchemaProvider> EP_NAME = ExtensionPointName.create("com.intellij.xml.html5SchemaProvider");

  public static final String HTML5_SCHEMA_LOCATION;
  public static final String XHTML5_SCHEMA_LOCATION;

  @NotNull
  public abstract URL getHtmlSchemaLocation();

  @NotNull
  public abstract URL getXhtmlSchemaLocation();

  static {
    final Html5SchemaProvider[] providers = EP_NAME.getExtensions();
    final URL htmlSchemaLocationURL;
    final URL xhtmlSchemaLocationURL;

    LOG.assertTrue(providers.length > 0, "RelaxNG based schema for HTML5 is not supported. Old XSD schema will be used");

    if (providers.length > 1) {
      LOG.error("More than one HTML5 schema providers found: " + getClassesListString(providers));
    }

    if (providers.length > 0) {
      htmlSchemaLocationURL = providers[0].getHtmlSchemaLocation();
      xhtmlSchemaLocationURL = providers[0].getXhtmlSchemaLocation();
    }
    else {
      htmlSchemaLocationURL = Html5SchemaProvider.class.getResource(ExternalResourceManagerImpl.STANDARD_SCHEMAS + "html5/xhtml5.xsd");
      xhtmlSchemaLocationURL = htmlSchemaLocationURL;
    }

    HTML5_SCHEMA_LOCATION =
      VfsUtil.urlToPath(VfsUtil.fixURLforIDEA(FileUtil.unquote(htmlSchemaLocationURL.toExternalForm())));
    LOG.info("HTML5_SCHEMA_LOCATION = " + HTML5_SCHEMA_LOCATION);

    XHTML5_SCHEMA_LOCATION =
      VfsUtil.urlToPath(VfsUtil.fixURLforIDEA(FileUtil.unquote(xhtmlSchemaLocationURL.toExternalForm())));
    LOG.info("XHTML5_SCHEMA_LOCATION = " + XHTML5_SCHEMA_LOCATION);
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

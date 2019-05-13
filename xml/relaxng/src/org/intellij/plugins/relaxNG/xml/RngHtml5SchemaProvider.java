package org.intellij.plugins.relaxNG.xml;

import com.intellij.xml.Html5SchemaProvider;
import org.jetbrains.annotations.NotNull;

import java.net.URL;

/**
 * @author Eugene.Kudelevsky
 */
public class RngHtml5SchemaProvider extends Html5SchemaProvider {
  @NotNull
  @Override
  public URL getHtmlSchemaLocation() {
    return RngHtml5SchemaProvider.class.getResource("/resources/html5-schema/html5.rnc");
  }

  @NotNull
  @Override
  public URL getXhtmlSchemaLocation() {
    return RngHtml5SchemaProvider.class.getResource("/resources/html5-schema/xhtml5.rnc");
  }

  @NotNull
  @Override
  public URL getCharsLocation() {
    return RngHtml5SchemaProvider.class.getResource("/resources/html5-schema/html5chars.ent");
  }
}

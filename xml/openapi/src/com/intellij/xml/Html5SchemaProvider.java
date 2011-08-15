package com.intellij.xml;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.net.URL;

/**
 * @author Eugene.Kudelevsky
 */
public interface Html5SchemaProvider {
  ExtensionPointName<Html5SchemaProvider> EP_NAME = ExtensionPointName.create("com.intellij.xml.html5SchemaProvider");

  @NotNull
  URL getHtmlSchemaLocation();

  @NotNull
  URL getXhtmlSchemaLocation();
}

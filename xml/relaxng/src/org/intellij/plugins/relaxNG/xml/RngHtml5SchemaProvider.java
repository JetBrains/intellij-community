// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.relaxNG.xml;

import com.intellij.xml.Html5SchemaProvider;
import org.jetbrains.annotations.NotNull;

import java.net.URL;

public class RngHtml5SchemaProvider extends Html5SchemaProvider {
  @Override
  public @NotNull URL getHtmlSchemaLocation() {
    return RngHtml5SchemaProvider.class.getResource("/resources/html5-schema/html5.rnc");
  }

  @Override
  public @NotNull URL getXhtmlSchemaLocation() {
    return RngHtml5SchemaProvider.class.getResource("/resources/html5-schema/xhtml5.rnc");
  }

  @Override
  public @NotNull URL getCharsLocation() {
    return RngHtml5SchemaProvider.class.getResource("/resources/html5-schema/html5chars.ent");
  }
}

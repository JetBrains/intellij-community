// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.relaxNG.xml;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.PathManager;
import com.intellij.util.PathUtil;
import com.intellij.xml.Html5SchemaProvider;
import kotlin.text.StringsKt;
import org.jetbrains.annotations.NotNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;

public class RngHtml5SchemaProvider extends Html5SchemaProvider {
  @Override
  public @NotNull URL getHtmlSchemaLocation() {
    return getResource("/resources/html5-schema/html5.rnc");
  }

  @Override
  public @NotNull URL getXhtmlSchemaLocation() {
    return getResource("/resources/html5-schema/xhtml5.rnc");
  }

  @Override
  public @NotNull URL getCharsLocation() {
    return getResource("/resources/html5-schema/html5chars.ent");
  }

  private static URL getResource(String name) {
    if (PluginManagerCore.isRunningFromSources() && PathUtil.getJarPathForClass(RngHtml5SchemaProvider.class).endsWith(".jar")) {
      Path community = Path.of(PathManager.getCommunityHomePath());
      try {
        name = StringsKt.removePrefix(name, "/");
        return community.resolve("xml/relaxng/resources").resolve(name).toUri().toURL();
      }
      catch (MalformedURLException e) {
        throw new RuntimeException(e);
      }
    }
    URL resource = RngHtml5SchemaProvider.class.getResource(name);
    if (resource == null) {
      throw new IllegalStateException("Resource '" + name + "' not found in classpath of " + RngHtml5SchemaProvider.class);
    }
    return resource;
  }
}

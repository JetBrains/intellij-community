// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javaee;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.apache.xml.resolver.Catalog;
import org.apache.xml.resolver.CatalogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.PropertyResourceBundle;

/**
 * @author Dmitry Avdeev
 */
public class XMLCatalogManager {

  private static final Logger LOG = Logger.getInstance(XMLCatalogManager.class);

  private static final Field ourResources;
  private static final Field ourPropertyFileUri;

  static {
    try {
      ourResources = CatalogManager.class.getDeclaredField("resources");
      ourResources.setAccessible(true);
      ourPropertyFileUri = CatalogManager.class.getDeclaredField("propertyFileURI");
      ourPropertyFileUri.setAccessible(true);
    }
    catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  private final CatalogManager myManager = new CatalogManager();

  public XMLCatalogManager(@NotNull String propertiesFilePath) {

    File file = new File(propertiesFilePath);
    try {
      String s = FileUtil.loadFile(file);
      PropertyResourceBundle bundle = new PropertyResourceBundle(new StringReader(s));
      ourResources.set(myManager, bundle);
      ourPropertyFileUri.set(myManager, file.toURI().toURL());
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  public @Nullable String resolve(String uri) {
    try {
      Catalog catalog = myManager.getCatalog();
      if (catalog == null) return null;
      String byUri = catalog.resolveURI(uri);
      if (byUri != null) return byUri;
      String resolved = catalog.resolveSystem(uri);
      return resolved == null ? catalog.resolvePublic(uri, null) : resolved;
    }
    catch (IOException e) {
      LOG.warn(e);
      return null;
    }
  }

  @TestOnly
  public CatalogManager getManager() {
    return myManager;
  }
}

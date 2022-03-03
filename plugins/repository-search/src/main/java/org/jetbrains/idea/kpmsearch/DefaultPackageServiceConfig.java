// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.kpmsearch;

import com.fasterxml.jackson.jr.ob.JSON;
import com.intellij.application.options.RegistryManager;
import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.concurrency.SameThreadExecutor;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service(Service.Level.APP)
public final class DefaultPackageServiceConfig implements PackageSearchEndpointConfig {
  private static final long INFO_TTL = TimeUnit.DAYS.toMillis(7);
  private static final String CONFIG_URL_KEY_PREFIX = DefaultPackageServiceConfig.class.getSimpleName().toLowerCase(Locale.ENGLISH) + ".";
  private static final String CONFIG_UPDATED_TIMESTAMP_KEY = CONFIG_URL_KEY_PREFIX + "updated.ts";

  private volatile @NotNull PackageSearchEndpointUrls packageSearchEndpointUrls = new PackageSearchEndpointUrls(null, null);

  private DefaultPackageServiceConfig() {
    if (!ApplicationManager.getApplication().isUnitTestMode() && RegistryManager.getInstance().is("maven.packagesearch.enabled")) {
      updateIfNecessary();
    }
  }

  private CompletableFuture<PackageSearchEndpointUrls> reloadConfig() {
    return CompletableFuture.supplyAsync(() -> {
      Map<String, Object> config;
      try {
        config = HttpRequests.request(Objects.requireNonNull(RegistryManager.getInstance().stringValue("packagesearch.config.url")))
          .userAgent(getUserAgent())
          .forceHttps(true)
          .connect(request -> JSON.std.mapFrom(request.getReader()));
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      Object idea = config.get("idea");
      if (!(idea instanceof Map)) {
        return new PackageSearchEndpointUrls(null, null);
      }

      @SuppressWarnings("unchecked")
      Map<String, Object> o = (Map<String, Object>)idea;
      String fulltextUrl = extract("fulltext", o);
      String suggestUrl = extract("suggest", o);
      return new PackageSearchEndpointUrls(fulltextUrl, suggestUrl);
    }, ApplicationManager.getApplication().isDispatchThread() ? ProcessIOExecutorService.INSTANCE : SameThreadExecutor.INSTANCE);
  }

  @Override
  public String getFullTextUrl() {
    return packageSearchEndpointUrls.fulltextUrl;
  }

  @Override
  public String getSuggestUrl() {
    return packageSearchEndpointUrls.suggestUrl;
  }

  @Override
  public int getReadTimeout() {
    return Registry.intValue("packagesearch.timeout");
  }

  @Override
  public int getConnectTimeout() {
    return getReadTimeout();
  }

  private static String extract(String fulltext, Map<String, Object> idea) {
    Object urlMap = idea.get(fulltext);
    if (urlMap instanceof Map) {
      //noinspection unchecked
      if (Boolean.valueOf(String.valueOf(((Map<String, Object>)urlMap).get("enabled")))) {
        @SuppressWarnings("unchecked")
        Object url = ((Map<String, ?>)urlMap).get("url");
        if (url instanceof String) {
          return url.toString();
        }
      }
    }
    return null;
  }

  private void updateIfNecessary() {
    PropertiesComponent persistentCache = PropertiesComponent.getInstance();
    long updatedTimestamp = persistentCache.getLong(CONFIG_UPDATED_TIMESTAMP_KEY, 0);
    if ((System.currentTimeMillis() - updatedTimestamp) > INFO_TTL) {
      reloadConfig().thenAccept(result -> {
        persistentCache.setValue(CONFIG_UPDATED_TIMESTAMP_KEY, String.valueOf(System.currentTimeMillis()));
        persistentCache.setValue(CONFIG_URL_KEY_PREFIX + "fulltext", result.fulltextUrl);
        persistentCache.setValue(CONFIG_URL_KEY_PREFIX + "suggest", result.suggestUrl);
        packageSearchEndpointUrls = result;
      });
    }
    else {
      packageSearchEndpointUrls = new PackageSearchEndpointUrls(
        persistentCache.getValue(CONFIG_URL_KEY_PREFIX + "fulltext"),
        persistentCache.getValue(CONFIG_URL_KEY_PREFIX + "suggest")
      );
    }
  }

  static final class PackageSearchEndpointUrls {
    final String fulltextUrl;
    final String suggestUrl;

    PackageSearchEndpointUrls(@Nullable String fulltextUrl, @Nullable String suggestUrl) {
      this.fulltextUrl = fulltextUrl;
      this.suggestUrl = suggestUrl;
    }
  }
}

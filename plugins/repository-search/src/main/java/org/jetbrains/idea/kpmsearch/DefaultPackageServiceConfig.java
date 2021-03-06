// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.kpmsearch;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class DefaultPackageServiceConfig implements PackageSearchEndpointConfig {
  private static final Object MUTEX = new Object();
  public final static long TIMEOUT = Registry.intValue("packagesearch.timeout");


  private final static long INFO_TTL = TimeUnit.DAYS.toMillis(7);
  private final static String CONFIG_URL = Registry.stringValue("packagesearch.config.url");
  private final static String CONFIG_UPDATED_TIMESTAMP_KEY = "packagesearch.status.updated.ts";
  private final static String CONFIG_URL_KEY_PREFIX = "packagesearch.endpoint.url.";

  private static final Gson GSON = new Gson();
  private volatile PackageSearchEndpointUrls myPackageSearchEndpointUrls;

  public DefaultPackageServiceConfig() {
    if (ApplicationManager.getApplication().isUnitTestMode() || !Registry.is("maven.packagesearch.enabled")) {
      myPackageSearchEndpointUrls = null;
      return;
    }
    updateIfNessesary();
  }

  @SuppressWarnings("unchecked")
  private Promise<PackageSearchEndpointUrls> reloadConfig() {

    AsyncPromise<PackageSearchEndpointUrls> asyncPromise = new AsyncPromise<>();
    asyncPromise.onError(t -> {});
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        LinkedTreeMap config = GSON.fromJson(
          HttpRequests.request(CONFIG_URL)
            .userAgent(getUserAgent())
            .forceHttps(true)
            .readString(), LinkedTreeMap.class);
        Object idea = config.get("idea");
        if (!(idea instanceof Map)) {
          asyncPromise.setResult(new PackageSearchEndpointUrls(null, null));
          return;
        }

        String fulltextUrl = extract("fulltext", (Map<Object, Object>)idea);
        String suggestUrl = extract("suggest", (Map<Object, Object>)idea);
        asyncPromise.setResult(new PackageSearchEndpointUrls(fulltextUrl, suggestUrl));
      }
      catch (Exception e) {
        asyncPromise.setError(e);
      }
    });
    return asyncPromise;
  }

  @Override
  public String getFullTextUrl() {
    if (myPackageSearchEndpointUrls == null){
      return null;
    }
    return myPackageSearchEndpointUrls.fulltextUrl;
  }

  @Override
  public String getSuggestUrl() {
    if (myPackageSearchEndpointUrls == null){
      return null;
    }
    return myPackageSearchEndpointUrls.suggestUrl;
  }

  @Override
  public int getReadTimeout() {
    return (int)TIMEOUT;
  }

  @Override
  public int getConnectTimeout() {
    return (int)TIMEOUT;
  }

  private static String extract(String fulltext, Map<Object, Object> idea) {
    Object urlMap = idea.get(fulltext);
    if (urlMap instanceof Map) {
      Object enabled = ((Map)urlMap).get("enabled");
      if (Boolean.valueOf(String.valueOf(enabled))) {
        Object url = ((Map)urlMap).get("url");
        if (url instanceof String) {
          return url.toString();
        }
      }
    }
    return null;
  }


  private void updateIfNessesary() {
    synchronized (MUTEX) {
      long updatedTimestamp = PropertiesComponent.getInstance().getLong(CONFIG_UPDATED_TIMESTAMP_KEY, 0);
      if (System.currentTimeMillis() - updatedTimestamp > INFO_TTL) {
        reloadConfig().onSuccess(eu -> {
          PropertiesComponent.getInstance().setValue(CONFIG_UPDATED_TIMESTAMP_KEY, String.valueOf(System.currentTimeMillis()));
          PropertiesComponent.getInstance().setValue(CONFIG_URL_KEY_PREFIX + "fulltext", eu.fulltextUrl);
          PropertiesComponent.getInstance().setValue(CONFIG_URL_KEY_PREFIX + "suggest", eu.suggestUrl);
          myPackageSearchEndpointUrls = eu;
          myPackageSearchEndpointUrls = loadPersistedResult();
        });
      }
      else {
        myPackageSearchEndpointUrls = loadPersistedResult();
      }
    }
  }

  @NotNull
  private static PackageSearchEndpointUrls loadPersistedResult() {
    return new PackageSearchEndpointUrls(
      PropertiesComponent.getInstance().getValue(CONFIG_URL_KEY_PREFIX + "fulltext"),
      PropertiesComponent.getInstance().getValue(CONFIG_URL_KEY_PREFIX + "suggest"));
  }


  static class PackageSearchEndpointUrls {
    final String fulltextUrl;
    final String suggestUrl;

    PackageSearchEndpointUrls(String fulltextUrl, String suggestUrl) {
      this.fulltextUrl = fulltextUrl;
      this.suggestUrl = suggestUrl;
    }
  }
}

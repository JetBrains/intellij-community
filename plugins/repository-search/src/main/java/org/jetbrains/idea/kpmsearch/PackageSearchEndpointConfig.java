package org.jetbrains.idea.kpmsearch;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PackageSearchEndpointConfig {
  @Nullable String getFullTextUrl();

  @Nullable String getSuggestUrl();

  default @NotNull String getUserAgent() {
    return ApplicationNamesInfo.getInstance().getProductName() + "/" + ApplicationInfo.getInstance().getFullVersion();
  }

  int getReadTimeout();

  int getConnectTimeout();

  default boolean forceHttps(){
    return true;
  }
}

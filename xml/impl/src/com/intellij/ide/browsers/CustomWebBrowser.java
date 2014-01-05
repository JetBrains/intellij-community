package com.intellij.ide.browsers;

import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.UUID;

final class CustomWebBrowser extends WebBrowser {
  private final Computable<String> pathComputable;
  private final Icon icon;
  private final String browserNotFoundMessage;

  CustomWebBrowser(@NotNull BrowsersConfiguration.BrowserFamily family,
                   @NotNull String name,
                   @NotNull Icon icon,
                   @NotNull Computable<String> pathComputable,
                   @Nullable String browserNotFoundMessage) {
    super(family, name);

    this.pathComputable = pathComputable;
    this.icon = icon;
    this.browserNotFoundMessage = browserNotFoundMessage;
  }

  @NotNull
  @Override
  public UUID getId() {
    // todo - refine Dartium usage
    throw new IllegalStateException("Custom browser must not be referenced in this way");
  }

  @Override
  @NotNull
  public Icon getIcon() {
    return icon;
  }

  @Override
  @Nullable
  public String getPath() {
    return pathComputable.compute();
  }

  @Override
  @NotNull
  public String getBrowserNotFoundMessage() {
    String message = browserNotFoundMessage;
    return message == null ? super.getBrowserNotFoundMessage() : message;
  }


}
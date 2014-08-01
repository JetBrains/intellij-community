package org.jetbrains.builtInWebServer;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpRequest;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public abstract class FileHandler {
  static final ExtensionPointName<FileHandler> EP_NAME = ExtensionPointName.create("org.jetbrains.webServerFileHandler");

  public abstract boolean process(@NotNull VirtualFile file,
                                  @NotNull String canonicalRequestPath,
                                  @NotNull Project project,
                                  @NotNull FullHttpRequest request,
                                  @NotNull Channel channel) throws IOException;
}
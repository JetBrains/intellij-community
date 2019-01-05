package de.plushnikov.intellij.plugin.util;

import com.intellij.openapi.extensions.Extensions;
import de.plushnikov.intellij.plugin.extension.LombokProcessorExtensionPoint;
import de.plushnikov.intellij.plugin.processor.Processor;
import org.jetbrains.annotations.NotNull;

public class ExtensionsUtil {
  @NotNull
  public static <U extends Processor> U findExtension(@NotNull Class<U> extClass) {
    return Extensions.findExtension(LombokProcessorExtensionPoint.EP_NAME_PROCESSOR, extClass);
  }
}

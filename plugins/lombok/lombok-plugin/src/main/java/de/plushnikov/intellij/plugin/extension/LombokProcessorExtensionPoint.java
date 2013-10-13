package de.plushnikov.intellij.plugin.extension;

import com.intellij.openapi.extensions.ExtensionPointName;
import de.plushnikov.intellij.plugin.processor.LombokProcessor;

/**
 * Date: 21.07.13 Time: 12:54
 */
public class LombokProcessorExtensionPoint {
  public static final ExtensionPointName<LombokProcessor> EP_NAME = ExtensionPointName.create("Lombook Plugin.processor");
}

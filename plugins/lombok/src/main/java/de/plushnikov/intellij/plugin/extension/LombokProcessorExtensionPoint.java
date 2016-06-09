package de.plushnikov.intellij.plugin.extension;

import com.intellij.openapi.extensions.ExtensionPointName;
import de.plushnikov.intellij.plugin.processor.Processor;
import de.plushnikov.intellij.plugin.processor.modifier.ModifierProcessor;

/**
 * Date: 21.07.13 Time: 12:54
 */
public class LombokProcessorExtensionPoint {
  public static final ExtensionPointName<Processor> EP_NAME_PROCESSOR = ExtensionPointName.create("Lombook Plugin.processor");
  public static final ExtensionPointName<ModifierProcessor> EP_NAME_MODIFIER_PROCESSOR = ExtensionPointName.create("Lombook Plugin.modifierProcessor");
}

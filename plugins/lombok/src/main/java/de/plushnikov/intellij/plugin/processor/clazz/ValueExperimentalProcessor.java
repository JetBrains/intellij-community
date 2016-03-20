package de.plushnikov.intellij.plugin.processor.clazz;

import de.plushnikov.intellij.plugin.processor.clazz.constructor.AllArgsConstructorProcessor;

/**
 * @author twillouer
 */
public class ValueExperimentalProcessor extends ValueProcessor {

  @SuppressWarnings("deprecation")
  public ValueExperimentalProcessor(GetterProcessor getterProcessor, EqualsAndHashCodeProcessor equalsAndHashCodeProcessor,
                                    ToStringProcessor toStringProcessor, AllArgsConstructorProcessor allArgsConstructorProcessor) {
    super(lombok.experimental.Value.class, getterProcessor, equalsAndHashCodeProcessor, toStringProcessor, allArgsConstructorProcessor);
  }

}

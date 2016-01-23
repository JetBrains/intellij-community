package de.plushnikov.intellij.plugin.processor.clazz;

/**
 * @author twillouer
 */
public class ValueExperimentalProcessor extends ValueProcessor {

  @SuppressWarnings("deprecation")
  public ValueExperimentalProcessor() {
    super(lombok.experimental.Value.class);
  }

}

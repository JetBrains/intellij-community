package de.plushnikov.intellij.plugin.agent.transformer;

import java.lang.instrument.ClassFileTransformer;

/**
 * @author Alexej Kubarev
 */
public interface IdeaPatcherTransformer extends ClassFileTransformer {
  boolean supported();
  boolean canRetransform();
}

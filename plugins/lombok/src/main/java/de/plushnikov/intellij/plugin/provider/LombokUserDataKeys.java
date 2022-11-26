package de.plushnikov.intellij.plugin.provider;

import com.intellij.openapi.util.Key;

import java.util.Collection;

public class LombokUserDataKeys {
  public static final Key<Collection<String>> AUGMENTED_ANNOTATIONS = Key.create("lombok.augmented.annotations");
}

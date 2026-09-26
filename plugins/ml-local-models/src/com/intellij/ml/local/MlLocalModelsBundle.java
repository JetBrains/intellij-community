package com.intellij.ml.local;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class MlLocalModelsBundle {
  private static final String ML_LOCAL_MODELS_BUNDLE = "messages.MlLocalModelsBundle";

  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = ML_LOCAL_MODELS_BUNDLE) String key, Object @NotNull ... params) {
    return ourInstance.getMessage(key, params);
  }

  private static final DynamicBundle ourInstance = new DynamicBundle(MlLocalModelsBundle.class, ML_LOCAL_MODELS_BUNDLE);
}

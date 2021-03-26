package de.plushnikov.intellij.plugin.activity;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class LombokPluginDisposable implements Disposable {

  public static Disposable getInstance(@NotNull Project project) {
    return project.getService(LombokPluginDisposable.class);
  }

  @Override
  public void dispose() { }
}

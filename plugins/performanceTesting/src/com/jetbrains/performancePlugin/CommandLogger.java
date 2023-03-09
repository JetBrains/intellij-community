package com.jetbrains.performancePlugin;

import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.PlaybackRunner.StatusCallback;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class CommandLogger implements StatusCallback {

  @Override
  public void message(@Nullable PlaybackContext context, String text, Type type) {
    if (type == Type.error) {
      System.err.println(text);
      if (System.getProperty("testscript.filename") != null) {
        ApplicationManagerEx.getApplicationEx().exit(true, true);
      }
    }
    else {
      System.out.println(text);
    }
  }
}

// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh;

import com.intellij.lang.Language;
import com.intellij.notification.NotificationGroup;

import static com.intellij.sh.ShBundle.message;

public final class ShLanguage extends Language {
  public static final Language INSTANCE = new ShLanguage();
  public static final String NOTIFICATION_GROUP_ID = NotificationGroup.createIdWithTitle("Shell Script", message("sh.shell.script"));

  public ShLanguage() {
    super("Shell Script", "application/x-bsh", "application/x-sh", "text/x-script.sh");
  }
}

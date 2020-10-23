/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.commandInterface.command;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Covers element help: text and external url (if exists)
 *
 * @author Ilya.Kazakevich
 */
public final class Help {
  @NotNull
  private final @NlsSafe String myHelpString;
  @Nullable
  private final @NlsSafe String myExternalHelpUrl;

  /**
   * @param helpString help text (no external url provided)
   */
  public Help(@NotNull @NlsSafe String helpString) {
    this(helpString, null);
  }

  /**
   * @param helpString      help text
   * @param externalHelpUrl external help url (if any)
   */
  public Help(@NotNull @NlsSafe String helpString, @Nullable @NlsSafe String externalHelpUrl) {
    myHelpString = helpString;
    myExternalHelpUrl = externalHelpUrl;
  }

  /**
   * @return help text
   */
  @NotNull
  public @NlsSafe String getHelpString() {
    return myHelpString;
  }

  /**
   * @return external help url (if any)
   */
  @Nullable
  public @NlsSafe String getExternalHelpUrl() {
    return myExternalHelpUrl;
  }
}

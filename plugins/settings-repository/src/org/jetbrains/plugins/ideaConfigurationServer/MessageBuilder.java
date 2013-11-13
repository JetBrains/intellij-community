/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.mac.MacMessages;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class MessageBuilder {
  private final String myMessage;
  private final String myTitle;
  private Project myProject;
  private Icon myIcon;
  private DialogWrapper.DoNotAskOption myDoNotAskOption;

  private MessageBuilder(@NotNull String title, @NotNull String message) {
    myTitle = title;
    myMessage = message;
  }

  public static MessageBuilder yesNo(@NotNull String title, @NotNull String message) {
    return new MessageBuilder(title, message);
  }

  public MessageBuilder project(@Nullable Project project) {
    myProject = project;
    return this;
  }

  /**
   * @see {@link com.intellij.openapi.ui.Messages#getInformationIcon()}
   * @see {@link com.intellij.openapi.ui.Messages#getWarningIcon()}
   * @see {@link com.intellij.openapi.ui.Messages#getErrorIcon()}
   * @see {@link com.intellij.openapi.ui.Messages#getQuestionIcon()}
   */
  public MessageBuilder icon(@Nullable Icon icon) {
    myIcon = icon;
    return this;
  }

  public MessageBuilder doNotAskOption(@NotNull DialogWrapper.DoNotAskOption doNotAskOption) {
    myDoNotAskOption = doNotAskOption;
    return this;
  }

  @MagicConstant(intValues = {Messages.YES, Messages.NO, Messages.CANCEL, Messages.OK})
  public int show() {
    if (Messages.canShowMacSheetPanel()) {
      return MacMessages.getInstance().showYesNoDialog(myTitle, myMessage, Messages.YES_BUTTON, Messages.NO_BUTTON, WindowManager.getInstance().suggestParentWindow(myProject), myDoNotAskOption);
    }
    else {
      //noinspection MagicConstant
      return Messages.showDialog(myProject, myMessage, myTitle, new String[]{Messages.YES_BUTTON, Messages.NO_BUTTON}, 0, myIcon, myDoNotAskOption);
    }
  }
}

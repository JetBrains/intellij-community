// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.ide.actions.runAnything.items.RunAnythingItemBase;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenCommandLineOptions.Option;

import javax.swing.*;
import java.awt.*;

import static com.intellij.openapi.util.text.StringUtil.*;

/**
 * @author ibessonov
 */
public class RunAnythingMavenItem extends RunAnythingItemBase {

  public RunAnythingMavenItem(@NotNull String command, @Nullable Icon icon) {
    super(command, icon);
  }

  @NotNull
  @Override
  public Component createComponent(boolean isSelected) {
    String command = getCommand();

    String toComplete = notNullize(substringAfterLast(command, " "));
    String description = null;
    if (toComplete.startsWith("-")) {
      Option option = MavenCommandLineOptions.findOption(toComplete);
      if (option != null) {
        description = option.getDescription();
      }
    }
    SimpleColoredComponent component = new SimpleColoredComponent();
    component.append(command);
    component.appendTextPadding(20);
    setupIcon(component, myIcon);

    if (description != null) {
      component.append(" " + shortenTextWithEllipsis(description, 200, 0), SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
    }
    return component;
  }
}
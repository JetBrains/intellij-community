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
  public Component createComponent(@Nullable String pattern, boolean isSelected, boolean hasFocus) {
    String command = getCommand();
    JPanel component = (JPanel)super.createComponent(pattern, isSelected, hasFocus);

    String toComplete = notNullize(substringAfterLast(command, " "));
    if (toComplete.startsWith("-")) {
      Option option = MavenCommandLineOptions.findOption(toComplete);
      if (option != null) {
        String description = option.getDescription();
        if (description != null) {
          SimpleColoredComponent descriptionComponent = new SimpleColoredComponent();
          //noinspection HardCodedStringLiteral
          descriptionComponent.append(" " + shortenTextWithEllipsis(description, 200, 0), SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
          component.add(descriptionComponent, BorderLayout.EAST);
        }
      }
    }

    return component;
  }
}
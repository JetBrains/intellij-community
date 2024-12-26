// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.model.presentation;

import com.intellij.ide.presentation.PresentationProvider;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.MavenDomGoal;
import org.jetbrains.idea.maven.dom.model.MavenDomGoals;

/**
 *
 */
public class MavenGoalsPresentationProvider extends PresentationProvider<MavenDomGoals> {

  @Override
  public @Nullable String getName(MavenDomGoals mavenDomGoals) {
    StringBuilder res = new StringBuilder("Goals");

    boolean hasGoals = false;

    for (MavenDomGoal mavenDomGoal : mavenDomGoals.getGoals()) {
      String goal = mavenDomGoal.getStringValue();

      if (!StringUtil.isEmptyOrSpaces(goal)) {
        if (hasGoals) {
          res.append(", ");
        }
        else {
          res.append(" (");
          hasGoals = true;
        }

        res.append(goal);
      }
    }

    if (hasGoals) {
      res.append(")");
    }

    return res.toString();
  }
}

/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.plugins.groovy;

import com.intellij.execution.filters.DefaultConsoleFiltersProvider;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Sergey Evdokimov
 */
public class MavenGroovyConsoleFilterProvider extends DefaultConsoleFiltersProvider {

  private static final Pattern PATTERN = Pattern.compile("\\[ERROR\\] (\\S.+\\.groovy): (-?\\d{1,5}): .+", Pattern.DOTALL);

  @Override
  public Filter[] getDefaultFilters(@NotNull final Project project, @NotNull GlobalSearchScope scope) {
    return new Filter[]{
      new Filter() {
        @Nullable
        @Override
        public Result applyFilter(String line, int entireLength) {

          // Example of gmaven error line:
          // [ERROR] /home/user/ideaProjects/simpleMaven/src/main/groovy/com/A.groovy: 17: [Static type checking] - Cannot assign value of type java.lang.String to variable of type int

          if (!line.startsWith("[ERROR] ") || !line.contains(".groovy: ")) return null;

          Matcher matcher = PATTERN.matcher(line);
          if (!matcher.matches()) return null;

          String path = matcher.group(1);

          VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
          if (file == null) return null;

          int lineNumber = Integer.parseInt(matcher.group(2)) - 1;
          if (lineNumber < 0) {
            lineNumber = -1;
          }

          TextAttributes attr = createCompilationErrorAttr();

          return new Result(entireLength - line.length() + matcher.start(1), entireLength - line.length() + matcher.end(2),
                            new OpenFileHyperlinkInfo(project, file, lineNumber), attr);
        }

        private TextAttributes createCompilationErrorAttr() {
          TextAttributes attr =
            EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES).clone();
          attr.setForegroundColor(Color.RED);
          attr.setEffectColor(Color.RED);
          attr.setEffectType(EffectType.LINE_UNDERSCORE);
          attr.setFontType(Font.PLAIN);
          return attr;
        }

      }
    };
  }

}

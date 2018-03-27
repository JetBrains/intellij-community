package com.jetbrains.python.edu.debugger;

import com.intellij.execution.filters.ConsoleInputFilterProvider;
import com.intellij.execution.filters.InputFilter;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class PyEduConsoleInputFilterProvider implements ConsoleInputFilterProvider {
  @NotNull
  @Override
  public InputFilter[] getDefaultFilters(@NotNull Project project) {
    return new InputFilter[]{new InputFilter() {
      @Override
      public List<Pair<String, ConsoleViewContentType>> applyFilter(@NotNull String text, @NotNull ConsoleViewContentType outputType) {
        if (outputType.equals(ConsoleViewContentType.SYSTEM_OUTPUT) && !text.contains("exit code")) {
          return Collections.emptyList();
        }
        if (text.startsWith("pydev debugger")) {
          return Collections.emptyList();
        }
        return Collections.singletonList(Pair.create(text, outputType));
      }
    }};
  }
}

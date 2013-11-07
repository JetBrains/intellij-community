package org.jetbrains.postfixCompletion.Infrastructure;

import com.intellij.codeInsight.lookup.*;
import org.jetbrains.annotations.*;

import java.util.*;

public interface PostfixTemplateProvider {
  void createItems(
    @NotNull final PostfixTemplateAcceptanceContext context,
    @NotNull final List<LookupElement> consumer);
}
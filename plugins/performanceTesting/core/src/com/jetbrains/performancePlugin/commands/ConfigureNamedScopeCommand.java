// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.psi.search.scope.packageSet.PackageSetFactory;
import com.intellij.psi.search.scope.packageSet.ParsingException;
import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;

import static org.jetbrains.concurrency.Promises.rejectedPromise;
import static org.jetbrains.concurrency.Promises.resolvedPromise;

public class ConfigureNamedScopeCommand extends AbstractCommand {

  public static final String PREFIX = CMD_PREFIX + "configureNamedScope";

  private final Options myOptions = new Options();

  public ConfigureNamedScopeCommand(String text, int line) {
    super(text, line);
    if (text.startsWith(PREFIX)) {
      Args.parse(myOptions, text.substring(PREFIX.length()).trim().split(" "), false);
    }
  }

  @Override
  protected @NotNull Promise<Object> _execute(@NotNull PlaybackContext context) {
    String scopeName = myOptions.scopeName;
    String pattern = myOptions.pattern;
    if (scopeName == null) {
      dumpError(context, "Missing scope name.");
      return rejectedPromise();
    }
    if (pattern == null) {
      dumpError(context, "Missing pattern.");
      return rejectedPromise();
    }
    Project project = context.getProject();
    if (NamedScopeManager.getInstance(project).getScope(scopeName) != null) {
      dumpError(context, "Scope " + scopeName + "already exists.");
      return rejectedPromise();
    }
    try {
      NamedScope newScope = NamedScopeManager.getInstance(project).createScope(scopeName, PackageSetFactory.getInstance().compile(pattern));
      NamedScopeManager.getInstance(project).addScope(newScope);
    }
    catch (ParsingException e) {
      dumpError(context, "Failed to parse scope pattern: " + pattern + "\n" + e.getMessage());
      throw new RuntimeException(e);
    }
    if (NamedScopeManager.getInstance(project).getScope(scopeName) == null) {
      dumpError(context, "Failed to create scope " + scopeName);
      return rejectedPromise();
    }
    return resolvedPromise();
  }

  static class Options {
    @Argument
    String scopeName;

    @Argument
    String pattern;
  }
}

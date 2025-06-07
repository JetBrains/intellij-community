// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.JBTreeTraverser;
import com.jetbrains.performancePlugin.PerformanceTestSpan;
import com.jetbrains.performancePlugin.utils.AbstractCallbackBasedCommand;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;


public abstract class ExpandMenuCommand extends AbstractCallbackBasedCommand {
  public ExpandMenuCommand(@NotNull String text, int line) {
    super(text, line, true);
  }

  @Override
  protected void execute(@NotNull ActionCallback callback, @NotNull PlaybackContext context)  {
    ActionManager actionManager = ActionManager.getInstance();
    Component focusedComponent = IdeFocusManager.findInstance().getFocusOwner(); // real focused component (editor/project view/..)
    DataContext dataContext = DataManager.getInstance().getDataContext(focusedComponent);
    ActionGroup mainMenu = (ActionGroup)actionManager.getAction(getGroupId());
    Span totalSpan = PerformanceTestSpan.TRACER.spanBuilder(getSpanName()).startSpan();
    JBTreeTraverser.<AnAction>from(action -> {
      totalSpan.addEvent(action.getClass().getSimpleName());
      if (!(action instanceof ActionGroup group)) return JBIterable.empty();
      String groupSpanName = ObjectUtils.coalesce(actionManager.getId(group), group.getTemplateText(), group.getClass().getName());
      Span groupSpan = PerformanceTestSpan.TRACER.spanBuilder(groupSpanName).setParent(Context.current().with(totalSpan)).startSpan();
      List<AnAction> actions = Utils.expandActionGroup(
        group, new PresentationFactory(), dataContext, getPlace(),
        ActionPlaces.isPopupPlace(getPlace()) ? ActionUiKind.POPUP : ActionUiKind.NONE);
      groupSpan.end();
      return actions;
    }).withRoots(mainMenu).traverse().size();
    totalSpan.end();
    callback.setDone();
  }

  protected abstract String getSpanName();

  protected abstract @NotNull String getGroupId();

  protected abstract @NotNull String getPlace();
}

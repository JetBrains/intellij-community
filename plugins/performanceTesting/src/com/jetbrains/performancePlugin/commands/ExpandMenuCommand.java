package com.jetbrains.performancePlugin.commands;

import com.intellij.diagnostic.telemetry.TraceUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
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
import org.jetbrains.annotations.NotNull;

import java.awt.*;


abstract public class ExpandMenuCommand extends AbstractCallbackBasedCommand {
  public ExpandMenuCommand(@NotNull String text, int line) {
    super(text, line, true);
  }

  @Override
  protected void execute(@NotNull ActionCallback callback,
                         @NotNull PlaybackContext context) throws Exception {
    ActionManager actionManager = ActionManager.getInstance();
    Component focusedComponent = IdeFocusManager.findInstance().getFocusOwner(); // real focused component (editor/project view/..)
    DataContext dataContext = Utils.wrapToAsyncDataContext(DataManager.getInstance().getDataContext(focusedComponent));
    ActionGroup mainMenu = (ActionGroup)actionManager.getAction(getGroupId());
    TraceUtil.runWithSpanThrows(PerformanceTestSpan.TRACER, getSpanName(), totalSpan -> {
      JBTreeTraverser.<AnAction>from(action -> {
        totalSpan.addEvent(action.getClass().getSimpleName());
        if (!(action instanceof ActionGroup group)) return JBIterable.empty();
        String groupSpanName = ObjectUtils.coalesce(actionManager.getId(group), group.getTemplateText(), group.getClass().getName());
        return TraceUtil.computeWithSpanThrows(PerformanceTestSpan.TRACER, groupSpanName, groupSpan -> {
          return Utils.expandActionGroup(group, new PresentationFactory(), dataContext, getPlace());
        });
      }).withRoots(mainMenu.getChildren(null)).traverse().size();
    });
    callback.setDone();
  }

  abstract protected  String getSpanName();

  @NotNull
  abstract protected String getGroupId();

  @NotNull
  abstract protected String getPlace();
}

package com.intellij.execution.junit2.ui.properties;

import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.config.ToggleBooleanProperty;

public class ScrollToTestSourceAction extends ToggleBooleanProperty.Disablable {
  private JUnitRunningModel myModel;
  public ScrollToTestSourceAction(final JUnitConsoleProperties properties) {
    super(ExecutionBundle.message("junit.auto.scroll.to.source.action.name"),
          ExecutionBundle.message("junit.open.text.in.editor.action.name"),
          IconLoader.getIcon("/general/autoscrollToSource.png"),
          properties, JUnitConsoleProperties.SCROLL_TO_SOURCE);
  }

  protected boolean isEnabled() {
    final AbstractProperty.AbstractPropertyContainer properties = getProperties();
    final JUnitRunningModel model = myModel;
    return isEnabled(properties, model);
  }

  private static boolean isEnabled(final AbstractProperty.AbstractPropertyContainer properties, final JUnitRunningModel model) {
    if (!JUnitConsoleProperties.TRACK_RUNNING_TEST.value(properties)) return true;
    return model != null && !model.getStatus().isRunning();
  }

  public static boolean isScrollEnabled(final JUnitRunningModel model) {
    final JUnitConsoleProperties properties = model.getProperties();
    return isEnabled(properties, model) && JUnitConsoleProperties.SCROLL_TO_SOURCE.value(properties);
  }

  public void setModel(final JUnitRunningModel model) {
    myModel = model;
  }
}

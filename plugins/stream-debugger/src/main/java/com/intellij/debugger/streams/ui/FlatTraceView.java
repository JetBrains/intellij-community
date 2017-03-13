package com.intellij.debugger.streams.ui;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
class FlatTraceView extends JPanel {
  FlatTraceView(@NotNull List<TraceController> controllers, @NotNull EvaluationContextImpl context) {
    super(new GridLayout(1, controllers.size()));
    for (final TraceController controller : controllers) {
      final CollectionView view = new CollectionView(controller.getCall().getName(), context, controller.getValues());
      controller.register(view);
      add(view);
    }
  }
}

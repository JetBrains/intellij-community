package org.intellij.plugins.markdown.ui.preview;

import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseWheelEvent;
import java.util.function.Supplier;

@ApiStatus.Internal
class PreciseVerticalScrollHelper extends MouseAdapter {
  private final @NotNull EditorImpl editor;
  private final @NotNull Supplier<? extends MarkdownHtmlPanelEx> htmlPanelSupplier;
  private int lastOffset = 0;

  PreciseVerticalScrollHelper(@NotNull EditorImpl editor, @NotNull Supplier<? extends MarkdownHtmlPanelEx> htmlPanelSupplier) {
    this.editor = editor;
    this.htmlPanelSupplier = htmlPanelSupplier;
  }

  @Override
  public void mouseWheelMoved(MouseWheelEvent event) {
    final var currentOffset = editor.getScrollingModel().getVerticalScrollOffset();
    if (lastOffset == currentOffset) {
      boundaryReached(event);
    } else {
      lastOffset = currentOffset;
    }
  }

  private void boundaryReached(MouseWheelEvent event) {
    final var actualPanel = htmlPanelSupplier.get();
    if (actualPanel == null) {
      return;
    }
    if (event.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL) {
      final var multiplier = Registry.intValue("ide.browser.jcef.osr.wheelRotation.factor", 1);
      final var amount = event.getScrollAmount() * event.getWheelRotation() * multiplier;
      actualPanel.scrollBy(0, amount);
    }
  }
}

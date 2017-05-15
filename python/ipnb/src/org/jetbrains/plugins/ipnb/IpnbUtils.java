package org.jetbrains.plugins.ipnb;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.util.TimeoutUtil;
import javafx.application.Platform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class IpnbUtils {
  private static final Logger LOG  = Logger.getInstance(IpnbUtils.class);
  private static int hasFx = 0;

  public static JComponent createLatexPane(@NotNull final String source, int width, IpnbFilePanel parent) {
    final JComponent panel = createHtmlPanel(source, width, parent);

    panel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        final Container parent = panel.getParent();
        final MouseEvent parentEvent = SwingUtilities.convertMouseEvent(panel, e, parent);
        parent.dispatchEvent(parentEvent);
      }
    });
    //TODO: jump to the section (see User Interface#Utilities)

    return panel;
  }

  public static boolean hasFx() {
    if (hasFx == 0) {
      try {
        Platform.setImplicitExit(false);
        hasFx = 1;
      }
      catch (NoClassDefFoundError e) {
        hasFx = 2;
      }
    }
    return hasFx == 1;
  }

  public static JComponent createHtmlPanel(@NotNull final String source, int width, IpnbFilePanel parent) {
    if (hasFx()) {
      return IpnbJfxUtils.createHtmlPanel(source, width, parent);
    }
    return createNonJfxPanel(source);
  }

  public static JComponent createNonJfxPanel(@NotNull final String source) {
    final JTextArea textArea = new JTextArea(source);
    textArea.setLineWrap(true);
    textArea.setEditable(false);
    textArea.setBorder(BorderFactory.createLineBorder(JBColor.lightGray));
    textArea.setBackground(IpnbEditorUtil.getBackground());
    return textArea;
  }

  @SuppressWarnings("Duplicates")
  @Nullable
  public static <T> T execCancelable(@NotNull final Callable<T> callable) {
    final Future<T> future = ApplicationManager.getApplication().executeOnPooledThread(callable);

    while (!future.isCancelled() && !future.isDone()) {
      ProgressManager.checkCanceled();
      TimeoutUtil.sleep(500);
    }
    T result = null;
    try {
      result = future.get();
    }
    catch (InterruptedException | ExecutionException e) {
      LOG.warn(e.getMessage());
    }
    return result;
  }
  
  @Nullable
  public static <T> T runCancellableProcessUnderProgress(@NotNull Project project, @NotNull Callable<T> callable, 
                                                         @SuppressWarnings("SameParameterValue") @NotNull String title) {
    return ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      ProgressManager.getInstance().getProgressIndicator().setIndeterminate(true);
      return execCancelable(callable);
    }, title, true, project);
  }
}

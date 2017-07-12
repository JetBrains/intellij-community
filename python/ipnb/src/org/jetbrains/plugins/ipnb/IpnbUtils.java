package org.jetbrains.plugins.ipnb;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.configuration.IpnbSettings;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class IpnbUtils {
  private static final Logger LOG  = Logger.getInstance(IpnbUtils.class);

  public static JComponent createLatexPane(@NotNull final String source, Project project, int width) {
    final JComponent panel = createHtmlPanel(source, project, width);

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

  public static JComponent createHtmlPanel(@NotNull final String source, Project project, int width) {
    if (IpnbSettings.getInstance(project).hasFx()) {
      return IpnbJfxUtils.createHtmlPanel(source, width);
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

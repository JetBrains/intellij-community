import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;

/** Handles right-click in project view; applies rearrangement to a directory and its files recursively. */
public final class ProjectTreeActionHandler
  extends AnAction
{
  private static final Logger logger = Logger.getLogger("com.wrq.rearranger.ProjectTreeActionHandler");


  public final void actionPerformed(final AnActionEvent anActionEvent) {
    logger.debug("entered actionPerformed");
    // we're being called on the Swing event dispatch thread.  Spin off another thread to do the
    // rearranging and let Swing's thread go.
//        Thread t = new Thread(new Runnable() {
//            public void run()
//            {
//                //To change body of implemented methods use Options | File Templates.
    final Application application = ApplicationManager.getApplication();
    application.runWriteAction(new RearrangeIt(anActionEvent.getDataContext()));
//            }
//        }, "RearrangerThread");
//        t.start();
//        new RearrangeIt(anActionEvent.getDataContext()).run();
  }

  /**
   * Test progress bar functionality.  Read 10 "filenames" from console, pausing between each to update the
   * progress bar dialog.  Handle cancel button and dialog window close button properly.
   *
   * @param args
   */
  public static void main(final String[] args) {
    org.apache.log4j.BasicConfigurator.configure();
    // test the progress bar.
    final BufferedReader reader;
    final ProjectTreeActionHandler ptah = new ProjectTreeActionHandler();
    final RearrangeIt ri = ptah.new RearrangeIt(null);
    final RearrangeIt.IntHolder cancelled = ri.new IntHolder();

    reader = new BufferedReader(new InputStreamReader(System.in));
    final Cancellable c = new Cancellable() {
      public void setCancel() {
        System.out.println("cancel test");
        cancelled.n = 1;
      }
    };

    final JDialog dialog = ri.getProgressFrame(c, 10);
    int count = 0;
    dialog.pack();
    dialog.setVisible(true);
    while (count <= 10 && cancelled.n == 0) {
      String s = null;
      try {
        s = reader.readLine();
      }
      catch (IOException e) {
        e.printStackTrace();
      }
      if (cancelled.n == 0) {
        ri.filename.setText(s);
        count++;
        ri.bar.setValue(count);
      }
    }
    dialog.setVisible(false);
    dialog.hide();
    dialog.dispose();
    System.exit(0);
  }

  interface Cancellable {
    void setCancel();
  }

  abstract class VirtualFileVisitor implements Cancellable {
    volatile boolean cancel = false;

    public final void setCancel() {
      logger.debug("VirtualFileVisitor.setCancel called");
      cancel = true;
    }

    abstract void visitVirtualFile(VirtualFile f);

    final void accept(final VirtualFile f) {
      if (f != null) {
        visitVirtualFile(f);
      }
      final VirtualFile[] vfa = f.getChildren();
      if (vfa != null) {
        for (int i = 0; i < vfa.length; i++) {
          if (!cancel) {
            accept(vfa[i]);
          }
        }
      }
    }
  }

  final class RearrangeIt implements Runnable {
    final class IntHolder {
      public int n;
    }

    final DataContext dc;
    final Project     project;
    final JProgressBar bar      = new JProgressBar(JProgressBar.HORIZONTAL);
    final JLabel       filename = new JLabel();

    public RearrangeIt(final DataContext dc) {
      this.dc = dc;
      this.project = (Project)dc.getData(DataConstants.PROJECT);
    }

    public final void run() {
      final VirtualFile virtualFile = (VirtualFile)dc.getData(DataConstants.VIRTUAL_FILE);
      final IntHolder count = new IntHolder();
      final VirtualFileVisitor counter = new VirtualFileVisitor() {
        void visitVirtualFile(final VirtualFile f) {
          if (!f.isDirectory()) count.n++;
        }
      };

      counter.accept(virtualFile);
      logger.debug("counted " + count.n + " files");

      final RearrangerActionHandler rah = new RearrangerActionHandler();
      final PsiDocumentManager dm = PsiDocumentManager.getInstance(project);
      final PsiManager pm = PsiManager.getInstance(project);
      final VirtualFileVisitor executor = new VirtualFileVisitor() {
        int currentCount = 0;

        void visitVirtualFile(final VirtualFile f) {
          try {
            final PsiFile psiFile = pm.findFile(f);
            if (psiFile != null && psiFile.canContainJavaCode()) {
              logger.debug("rearrange psiFile=" + psiFile.toString());
              if (SwingUtilities.isEventDispatchThread()) {
                logger.debug("event dispatch thread setting filename to " + psiFile.getName());
                filename.setText(psiFile.getName());
              }
              else {
                logger.debug("invokeAndWait to set filename to " + psiFile.getName());
                SwingUtilities.invokeAndWait(new Runnable() {
                  public void run() {
                    logger.debug("event dispatch thread within invokeAndWait setting filename to " +
                                 psiFile.getName());
                    filename.setText(psiFile.getName());
                  }
                });
              }
              filename.repaint();
//                        filename.setText(psiFile.getName());
//                            filename.paint(filename.getGraphics());
              filename.paintImmediately(filename.getVisibleRect());
              Thread.sleep(8000);
              final Document document = dm.getDocument(psiFile);
              if (document == null) {
                logger.debug("document was null");
              }
              else {
//                                Thread t = new Thread(new Runnable() {
//                                    public void run()
//                                    {
                //To change body of implemented methods use Options | File Templates.
                rah.runWriteActionRearrangement(project, document, psiFile);
//                                    }
//                                }  );
//                                t.start();
              }
              logger.debug("done rearranging psiFile=" + psiFile.toString());
            }
            if (SwingUtilities.isEventDispatchThread()) {
              logger.debug("EventDispatchThread updating progress bar value to " + currentCount);
              bar.setValue(currentCount);
            }
            else {
              logger.debug("invokeAndWait to set progress bar value to " + currentCount);
              SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                  logger.debug("EventDispatchThread within InvokeAndWait setting progress bar value to " +
                               currentCount);
                  bar.setValue(currentCount);
                }
              });
            }
            bar.repaint();
            bar.paintImmediately(bar.getVisibleRect());
            bar.paint(bar.getGraphics());
            currentCount++;
            Thread.sleep(5000);
          }
          catch (InterruptedException e) {
            e.printStackTrace(); //To change body of catch statement use Options | File Templates.
          }
          catch (InvocationTargetException e) {
            e.printStackTrace(); //To change body of catch statement use Options | File Templates.
          }
        }
      };

      final JDialog dialog = getProgressFrame(executor, count.n);

      dialog.show();
      dialog.setVisible(true);
      dialog.paint(dialog.getGraphics());
      executor.accept(virtualFile);
      dialog.setVisible(false);
      dialog.hide();
      dialog.dispose();
    }

    final JDialog getProgressFrame(final Cancellable cancellableObject, final int max) {
      final Object parent = WindowManager.getInstance().suggestParentWindow(project);
      logger.debug("suggested parent window=" + parent);
      final JDialog dialog = parent == null ?
                             new JDialog() : (parent instanceof JDialog ?
                                              new JDialog((JDialog)parent) : new JDialog((JFrame)parent));
      final Container pane = dialog.getContentPane();
      final JLabel rearrangingLabel = new JLabel("Rearranging files, please wait...");
//        rearrangingLabel.setFont(new Font("dialog", Font.PLAIN, 12));
      final JPanel progressPanel = new JPanel(new GridBagLayout());
      final GridBagConstraints constraints = new GridBagConstraints();
      constraints.anchor = GridBagConstraints.NORTHWEST;
      constraints.gridx = 0;
      constraints.gridy = 0;
      constraints.fill = GridBagConstraints.NONE;
      constraints.gridwidth = GridBagConstraints.REMAINDER;
      constraints.gridheight = 1;
      constraints.weightx = 1;
      constraints.insets = new Insets(15, 15, 0, 0);
      progressPanel.add(rearrangingLabel, constraints);
      constraints.gridwidth = 1;
      constraints.gridy = 1;
      bar.setMinimum(0);
      bar.setMaximum(max);
      bar.setPreferredSize(new Dimension(500, 15));
      bar.setMinimumSize(new Dimension(500, 15));
      bar.setStringPainted(true);
      constraints.insets = new Insets(15, 15, 5, 15);
      progressPanel.add(bar, constraints);
      constraints.gridwidth = GridBagConstraints.REMAINDER;
      constraints.gridx = 1;
      constraints.insets = new Insets(9, 0, 5, 15);
      final JButton cancelButton = new JButton("Cancel");
      cancelButton.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          logger.debug("cancel button pressed");
          filename.setText("cancelling...");
          cancellableObject.setCancel();
        }
      });

      progressPanel.add(cancelButton, constraints);
      constraints.gridx = 0;
      constraints.gridy = 2;
      constraints.weighty = 1;
      constraints.anchor = GridBagConstraints.NORTHWEST;
      constraints.gridheight = GridBagConstraints.REMAINDER;
      constraints.insets = new Insets(0, 15, 15, 0);
      filename.setSize(500, 15);
      filename.setPreferredSize(new Dimension(500, 15));
//        filename.setFont(new Font("dialog", Font.PLAIN, 12));
      progressPanel.add(filename, constraints);
      pane.add(progressPanel, BorderLayout.CENTER);
      dialog.setTitle("Rearranger");
      dialog.addWindowListener(new WindowListener() {
        public void windowClosed(final WindowEvent e) {
          //To change body of implemented methods use Options | File Templates.
        }

        public void windowActivated(final WindowEvent e) {
          //To change body of implemented methods use Options | File Templates.
        }

        public void windowClosing(final WindowEvent e) {
          logger.debug("dialog closed, cancel Rearranger");
          filename.setText("cancelling...");
          cancellableObject.setCancel();
        }

        public void windowDeactivated(final WindowEvent e) {
          //To change body of implemented methods use Options | File Templates.
        }

        public void windowDeiconified(final WindowEvent e) {
          //To change body of implemented methods use Options | File Templates.
        }

        public void windowIconified(final WindowEvent e) {
          //To change body of implemented methods use Options | File Templates.
        }

        public void windowOpened(final WindowEvent e) {
          //To change body of implemented methods use Options | File Templates.
        }
      });

      dialog.pack();
      return dialog;
    }
  }
}

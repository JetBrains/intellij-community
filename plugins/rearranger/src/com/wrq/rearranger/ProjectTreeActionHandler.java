/*
 * Copyright (c) 2003, 2010, Dave Kriewall
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1) Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.wrq.rearranger;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.wrq.rearranger.settings.RearrangerSettings;
import com.wrq.rearranger.util.Constraints;

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
import java.util.ArrayList;
import java.util.List;

/** Handles right-click in project view; applies rearrangement to a directory and its files recursively. */
public final class ProjectTreeActionHandler
  extends AnAction
{
// ------------------------------ FIELDS ------------------------------

  private static final Logger LOG = Logger.getInstance("#" + ProjectTreeActionHandler.class.getName());

// -------------------------- OTHER METHODS --------------------------

  public final void actionPerformed(final AnActionEvent anActionEvent) {
    LOG.debug("entered actionPerformed");
    // we're being called on the Swing event dispatch thread.  Spin off another thread to do the 
    // rearranging and let Swing's thread go.
    Thread t = new Thread(
      new Runnable() {
        public void run() {
          new RearrangeIt(anActionEvent.getDataContext()).run();
        }
      }, "RearrangerThread"
    );
    t.start();
  }

// -------------------------- INNER CLASSES --------------------------

  abstract class VirtualFileVisitor {
    volatile boolean cancel = false;

    final void accept(final VirtualFile f) {
      if (f != null) {
        visitVirtualFile(f);
      }
      final VirtualFile[] vfa = f.getChildren();
      if (vfa != null) {
        for (VirtualFile aVfa : vfa) {
          if (!cancel) {
            accept(aVfa);
          }
        }
      }
    }

    abstract void visitVirtualFile(VirtualFile f);
  }

  final class RearrangeIt
    implements Runnable
  {
    final DataContext dc;
    final Project     project;
    final JProgressBar bar      = new JProgressBar(JProgressBar.HORIZONTAL);
    final JLabel       filename = new JLabel();

    class BoolHolder {
      boolean value;
    }

    public RearrangeIt(final DataContext dc) {
      this.dc = dc;
      if (dc != null) {
        this.project = (Project)dc.getData(DataConstants.PROJECT);
      }
      else {
        this.project = null;
      }
    }

    public final void run() {
      final VirtualFile virtualFile = (VirtualFile)dc.getData(DataConstants.VIRTUAL_FILE);
      final List<VirtualFile> files = new ArrayList<VirtualFile>();
      final IntHolder count = new IntHolder();
      final VirtualFileVisitor counter = new VirtualFileVisitor() {
        void visitVirtualFile(final VirtualFile file) {
          if (!file.isDirectory()) {
            count.n++;
            LOG.debug("" + count.n + ": file " + file.getName());
            files.add(file);
          }
        }
      };

      final Application application = ApplicationManager.getApplication();
      application.runReadAction(
        new Runnable() {
          public void run() {
            counter.accept(virtualFile);
          }
        }
      );

      LOG.debug("counted " + count.n + " files");
      final RearrangerActionHandler rah = new RearrangerActionHandler();
      final PsiDocumentManager dm = PsiDocumentManager.getInstance(project);
      final PsiManager pm = PsiManager.getInstance(project);
      final BoolHolder cancelled = new BoolHolder();
      final JDialog dialog = getProgressFrame(cancelled, count.n);
      dialog.setVisible(true);
      for (int currentCount = 0; currentCount < files.size(); currentCount++) {
        if (cancelled.value) {
          LOG.debug("cancelled rearrangement");
          break;
        }
        final VirtualFile f = files.get(currentCount);
        final int k = currentCount;
        Runnable fn = new Runnable() {
          public void run() {
            final PsiFile psiFile = pm.findFile(f);
            LOG.debug("SDT setting filename to " + psiFile.getName());
            filename.setText(psiFile.getName());
            filename.repaint();
          }
        };
        Runnable r = new Runnable() {
          public void run() {
            final PsiFile psiFile = pm.findFile(f);
            if (psiFile != null &&
                psiFile.getName().endsWith(".java") &&
                psiFile.isWritable())
            {
              LOG.debug("SDT rearranging file " + psiFile.getName());
              final Document document = dm.getDocument(psiFile);
              final Application application = ApplicationManager.getApplication();
              application.runWriteAction(
                new Runnable() {
                  public void run() {
                    final Application application = ApplicationManager.getApplication();
                    final Rearranger rearranger = application.getComponent(Rearranger.class);
                    RearrangerSettings settings = rearranger.getSettings();
                    settings = settings.deepCopy();
                    // avoid showing confirmation dialog for each file done
                    settings.setAskBeforeRearranging(false);
                    rah.runWriteActionRearrangement(project, document, psiFile, settings);
                  }

                  ;
                }
              );
            }
            ;
            if (!cancelled.value) {
              LOG.debug("SDT setting progress bar value to " + (k + 1));
              bar.setValue(k + 1);
              bar.repaint();
            }
          }
        };

        try {
          SwingUtilities.invokeAndWait(fn);
          SwingUtilities.invokeAndWait(r);
        }
        catch (InterruptedException e) {
          e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        catch (InvocationTargetException e) {
          e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
      }
      dialog.setVisible(false);
      dialog.dispose();
    }

    final JDialog getProgressFrame(final BoolHolder cancelled, final int max) {
      Object parent = null;
      if (project != null) {
        parent = WindowManager.getInstance().suggestParentWindow(project);
      }
      LOG.debug("suggested parent window=" + parent);
      final JDialog dialog = parent == null ?
                             new JDialog() :
                             (parent instanceof JDialog ?
                              new JDialog((JDialog)parent) :
                              new JDialog((JFrame)parent));
      final Container pane = dialog.getContentPane();
      final JLabel rearrangingLabel = new JLabel("Rearranging files, please wait...");
      final JPanel progressPanel = new JPanel(new GridBagLayout());
      final Constraints constraints = new Constraints(GridBagConstraints.NORTHWEST);
      constraints.newRow();
      constraints.insets = new Insets(15, 15, 0, 0);
      progressPanel.add(rearrangingLabel, constraints.weightedLastCol());
      constraints.newRow();
      bar.setMinimum(0);
      bar.setMaximum(max);
      bar.setPreferredSize(new Dimension(500, 15));
      bar.setMinimumSize(new Dimension(500, 15));
      bar.setStringPainted(true);
      constraints.insets = new Insets(15, 15, 5, 15);
      progressPanel.add(bar, constraints.weightedFirstCol());
      constraints.insets = new Insets(9, 0, 5, 15);
      final JButton cancelButton = new JButton("Cancel");
      cancelButton.addActionListener(
        new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            LOG.debug("cancel button pressed");
            filename.setText("cancelling...");
            cancelled.value = true;
          }
        }
      );

      progressPanel.add(cancelButton, constraints.weightedLastCol());
      constraints.weightedLastRow();
      constraints.insets = new Insets(0, 15, 15, 0);
      filename.setSize(500, 15);
      filename.setPreferredSize(new Dimension(500, 15));
//        filename.setFont(new Font("dialog", Font.PLAIN, 12));
      progressPanel.add(filename, constraints.weightedLastCol());
      pane.add(progressPanel, BorderLayout.CENTER);
      dialog.setTitle(Rearranger.COMPONENT_NAME);
      dialog.addWindowListener(
        new WindowListener() {
          public void windowClosed(final WindowEvent e) {
            //To change body of implemented methods use Options | File Templates.
          }

          public void windowActivated(final WindowEvent e) {
            //To change body of implemented methods use Options | File Templates.
          }

          public void windowClosing(final WindowEvent e) {
            LOG.debug("dialog closed, cancel Rearranger");
            filename.setText("cancelling...");
            cancelled.value = true;
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
        }
      );

      dialog.pack();
      return dialog;
    }

    final class IntHolder {
      public int n;
    }
  }

// --------------------------- main() method ---------------------------

  /**
   * Test progress bar functionality.  Read 10 "filenames" from console, pausing between each to update the
   * progress bar dialog.  Handle cancel button and dialog window close button properly.
   *
   * @param args
   */
  public static void main(final String[] args) {
// test the progress bar.
    final BufferedReader reader;
    final ProjectTreeActionHandler ptah = new ProjectTreeActionHandler();
    final RearrangeIt ri = ptah.new RearrangeIt(null);
    final RearrangeIt.IntHolder cancelled = ri.new IntHolder();
    reader = new BufferedReader(new InputStreamReader(System.in));
    RearrangeIt.BoolHolder c = ri.new BoolHolder();

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
    dialog.dispose();
    System.exit(0);
  }
}


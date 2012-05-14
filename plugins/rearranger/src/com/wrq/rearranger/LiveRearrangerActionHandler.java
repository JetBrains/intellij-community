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

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.wrq.rearranger.entry.ClassContentsEntry;
import com.wrq.rearranger.popup.ILiveRearranger;
import com.wrq.rearranger.popup.LiveRearrangerDialog;
import com.wrq.rearranger.popup.LiveRearrangerPopup;
import com.wrq.rearranger.rearrangement.Mover;
import com.wrq.rearranger.rearrangement.Parser;
import com.wrq.rearranger.ruleinstance.RuleInstance;
import com.wrq.rearranger.settings.RearrangerSettings;
import com.wrq.rearranger.util.CommentUtil;

import java.awt.*;
import java.util.List;

/** Supports manual rearrangement of a file. */
public final class LiveRearrangerActionHandler
  extends EditorActionHandler
{
// ------------------------------------------------------- FIELDS ------------------------------------------------------

  private static final Logger LOG = Logger.getInstance("#" + LiveRearrangerActionHandler.class.getName());

  private static       boolean inProgress = false;
  private final static boolean useDialog  = true; // set to false for Popup

// -------------------------- OTHER METHODS --------------------------

  public final void execute(final Editor editor, final DataContext context) {
    if (editor == null) {
      return;
    }
    final Project project = (Project)context.getData(DataConstants.PROJECT);
    LOG.debug("project=" + project);
    LOG.debug("editor=" + editor);
    final Document document = editor.getDocument();
    final CaretModel caret = editor.getCaretModel();
    int cursorOffset = caret.getOffset();
    final PsiFile psiFile = getFile(editor, context);
    if (!psiFile.getName().endsWith(".java")) {
      LOG.debug("not a .java file -- skipping " + psiFile.getName());
      return;
    }
    if (!RearrangerActionHandler.isFileWritable(psiFile)) {
      LOG.debug("not a writable .java file -- skipping " + psiFile.getName());
      return;
    }
    LOG.debug("inProgress=" + inProgress);
    if (!useDialog) {
      if (inProgress) {
        return;
      }
      setInProgress(true);
    }
    buildLiveRearrangerData(project, document, psiFile, cursorOffset);
  }

  private static PsiFile getFile(final Editor editor,
                                 final DataContext context)
  {
    final Project project = (Project)context.getData(DataConstants.PROJECT);
    final Document document = editor.getDocument();
    final FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    final VirtualFile virtualFile = fileDocumentManager.getFile(document);
    final PsiManager psiManager = PsiManager.getInstance(project);
    return psiManager.findFile(virtualFile);
  }

  public static void setInProgress(boolean inProgress) {
    LOG.debug("set inProgress=" + inProgress);
    LiveRearrangerActionHandler.inProgress = inProgress;
  }

  /**
   * must be called from within an IDEA read-action thread.
   *
   * @param project
   * @param document
   * @param psiFile
   */
  void buildLiveRearrangerData(final Project project,
                               final Document document,
                               final PsiFile psiFile,
                               final int cursorOffset)
  {
    /**
     * Per instructions from IntelliJ, we have to commit any changes to the document to the Psi
     * tree.
     */
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    documentManager.commitDocument(document);
    final RearrangerSettings settings = new RearrangerSettings(); // use default settings with no rules
    settings.setAskBeforeRearranging(true);
    settings.setRearrangeInnerClasses(true);
    if (useDialog) {
      final Application application = ApplicationManager.getApplication();
      application.runWriteAction(
        new Runnable() {
          public void run() {
            liveRearrangeDocument(project, psiFile, settings, document, cursorOffset);
          }
        }
      );
    }
    else {
      final Runnable task = new Runnable() {
        public void run() {
          LOG.debug("liveRearrangeDocument task started");
          liveRearrangeDocument(project, psiFile, settings, document, cursorOffset);
        }
      };

      Thread t = new Thread(
        new Runnable() {
          public void run() {
            LOG.debug("started thread " + Thread.currentThread().getName());
            final Application application = ApplicationManager.getApplication();
            application.runReadAction(
              new Runnable() {
                public void run() {
                  LOG.debug(
                    "enter application.runReadAction() on thread " +
                    Thread.currentThread().getName()
                  );
                  task.run();
                  LOG.debug(
                    "exit application.runReadAction() on thread " +
                    Thread.currentThread().getName()
                  );
                }
              }
            );
          }
        }, "Live Rearranger parser"
      );
      t.start();
    }
    LOG.debug("exit buildLiveRearrangerData on thread " + Thread.currentThread().getName());
  }

  public final void liveRearrangeDocument(final Project project,
                                          final PsiFile psiFile,
                                          final RearrangerSettings settings,
                                          final Document document,
                                          final int cursorOffset)
  {
    LOG.debug("enter liveRearrangeDocument on thread " + Thread.currentThread().getName());

    new CommentUtil(settings); // create CommentUtil singleton
    final Window window = WindowManager.getInstance().suggestParentWindow(project);
    ILiveRearranger fsp;
    if (useDialog) {
      fsp = new LiveRearrangerDialog(settings, psiFile, document, window, cursorOffset);
    }
    else {
      fsp = new LiveRearrangerPopup(settings, psiFile, document, project, window, cursorOffset);
    }
    final Parser p = new Parser(project, settings, psiFile);
    final List<ClassContentsEntry> outerClasses = p.parseOuterLevel();
    if (outerClasses.size() > 0) {
      final Mover mover = new Mover(outerClasses, settings);
      final List<RuleInstance> resultRuleInstances = mover.rearrangeOuterClasses();
      fsp.setResultRuleInstances(resultRuleInstances);
      fsp.liveRearranger();
    }
    LOG.debug("exit liveRearrangeDocument on thread " + Thread.currentThread().getName());
  }
}


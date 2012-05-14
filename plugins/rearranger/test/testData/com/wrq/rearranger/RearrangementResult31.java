/**
 * Id$
 *
 * Rearranger plugin for IntelliJ IDEA.
 *
 * Source code may be freely copied and reused.  Please copy credits, and send any bug fixes to the author.
 *
 * @author Dave Kriewall, WRQ, Inc.
 * January, 2004
 */
package com.wrq.rearranger;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.wrq.rearranger.popup.LiveRearrangerPopup;
import com.wrq.rearranger.rearrangement.Mover;
import com.wrq.rearranger.rearrangement.Parser;
import com.wrq.rearranger.settings.RearrangerSettings;
import com.wrq.rearranger.util.CommentUtil;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.ArrayList;

/** Supports manual rearrangement of a file. */
public final class LiveRearrangerActionHandler extends EditorActionHandler {
  // ------------------------------ FIELDS ------------------------------
  private static final Logger logger = Logger.getLogger("com.wrq.rearranger.LiveRearrangerActionHandler");

  private static boolean inProgress = false;
// -------------------------- STATIC METHODS --------------------------

  public static CodeStyleManager getCsm() {
    return RearrangerActionHandler.getCsm();
  }

// -------------------------- OTHER METHODS --------------------------

  public final void execute(final Editor editor, final DataContext context) {
    if (editor == null) {
      return;
    }
    final Project project = (Project)context.getData(DataConstants.PROJECT);
    logger.debug("project=" + project);
    logger.debug("editor=" + editor);
    final Document document = editor.getDocument();
    final CaretModel caret = editor.getCaretModel();
    int cursorOffset = caret.getOffset();
    final PsiFile psiFile = getFile(editor, context);
    if (!psiFile.getName().endsWith(".java")) {
      logger.debug("not a .java file -- skipping " + psiFile.getName());
      return;
    }
    if (!psiFile.isWritable()) {
      logger.debug("not a writable .java file -- skipping " + psiFile.getName());
      return;
    }
    logger.debug("inProgress=" + inProgress);
    if (inProgress) {
      return;
    }

    setInProgress(true);
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
    logger.debug("set inProgress=" + inProgress);
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
    final Runnable task = new Runnable() {
      public void run() {
        logger.debug("liveRearrangeDocument task started");
        liveRearrangeDocument(project, psiFile, settings, document, cursorOffset);
      }
    };

    Thread t = new Thread(new Runnable() {
      public void run() {
        logger.debug("started thread " + Thread.currentThread().getName());
        final Application application = ApplicationManager.getApplication();
        application.runReadAction(new Runnable() {
          public void run() {
            logger.debug("enter application.runReadAction() on thread " + Thread.currentThread().getName());
            task.run();
            logger.debug("exit application.runReadAction() on thread " + Thread.currentThread().getName());
          }
        });
      }
    }, "Live Rearranger parser");
    t.start();
    logger.debug("exit buildLiveRearrangerData on thread " + Thread.currentThread().getName());
  }

  public final void liveRearrangeDocument(final Project project,
                                          final PsiFile psiFile,
                                          final RearrangerSettings settings,
                                          final Document document,
                                          final int cursorOffset)
  {
    logger.setLevel(Level.DEBUG);   // TODO - remove debugging flag
    logger.debug("enter liveRearrangeDocument on thread " + Thread.currentThread().getName());

    RearrangerActionHandler.setCsm(CodeStyleManager.getInstance(project));
    new CommentUtil(settings); // create CommentUtil singleton
    final Window window = WindowManager.getInstance().suggestParentWindow(project);
    LiveRearrangerPopup fsp = new LiveRearrangerPopup(settings, psiFile, document, project, window, cursorOffset);
    final Parser p = new Parser(project, settings, psiFile);
    final ArrayList<ClassEntry> outerClasses = p.parseOuterLevel();
    if (outerClasses.size() > 0) {
      final Mover m = new Mover(outerClasses, settings);
      final ArrayList resultRuleInstances = m.rearrangeOuterClasses();
      fsp.setResultRuleInstances(resultRuleInstances);
      fsp.liveRearranger();
    }
    logger.debug("exit liveRearrangeDocument on thread " + Thread.currentThread().getName());
  }

  public boolean isFileWritable(PsiElement element) {
    VirtualFile file = element.getContainingFile().getVirtualFile();
    if (!file.isWritable()) {
      VirtualFileManager.getInstance().fireReadOnlyModificationAttempt(new VirtualFile[]{file});
      return file.isWritable();
    }
    return true;
  }
}

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
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.wrq.rearranger.entry.ClassContentsEntry;
import com.wrq.rearranger.popup.FileStructurePopup;
import com.wrq.rearranger.rearrangement.Emitter;
import com.wrq.rearranger.rearrangement.Mover;
import com.wrq.rearranger.rearrangement.Parser;
import com.wrq.rearranger.rearrangement.Spacer;
import com.wrq.rearranger.ruleinstance.RuleInstance;
import com.wrq.rearranger.settings.RearrangerSettings;
import com.wrq.rearranger.util.CommentUtil;

import java.awt.dnd.DragSource;
import java.util.List;

/** This class performs rearrangement actions requested by the user. */
public final class RearrangerActionHandler extends EditorActionHandler {

// ------------------------------ FIELDS ------------------------------

  private static final Logger LOG = Logger.getInstance("#" + RearrangerActionHandler.class.getName());
  private static int rightMargin;
  private static int tabSize;

// -------------------------- STATIC METHODS --------------------------

  public static int getRightMargin() {
    return rightMargin;
  }

  public static int getTabSize() {
    return tabSize;
  }

  public void setTabSize(int tabSize) {
    RearrangerActionHandler.tabSize = tabSize;
  }
// -------------------------- OTHER METHODS --------------------------

  public final void execute(final Editor editor, final DataContext context) {
    if (editor == null) {
      return;
    }
    LOG.debug("enter RearrangerActionHandler.execute()");
    final Project project = (Project)context.getData(DataConstants.PROJECT);
    final Document document = editor.getDocument();
    final PsiFile psiFile = getFile(editor, context);
    LOG.debug("suggested tool window = " +
              WindowManager.getInstance().suggestParentWindow(project));
    LOG.debug("drag source image supported = " + DragSource.isDragImageSupported());
    if (!psiFile.getName().endsWith(".java")) {
      LOG.debug("not a .java file -- skipping " + psiFile.getName());
      return;
    }
    if (!psiFile.isWritable()) {
      LOG.debug("not a writable .java file -- skipping " + psiFile.getName());
      return;
    }
    rightMargin = editor.getSettings().getRightMargin(project);
    tabSize = editor.getSettings().getTabSize(project);
    LOG.debug("right margin=" + rightMargin + ", tabSize=" + tabSize);
    final Application application = ApplicationManager.getApplication();
    application.runWriteAction(new Runnable() {
      public void run() {
        final Rearranger rearranger = ServiceManager.getService(Rearranger.class);
        final RearrangerSettings settings = rearranger.getSettings();
        runWriteActionRearrangement(project, document, psiFile, settings);
      }
    }
    );
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

  /**
   * must be called from within an IDEA write-action thread.
   *
   * @param project
   * @param document
   * @param psiFile
   */
  void runWriteActionRearrangement(final Project project,
                                   final Document document,
                                   final PsiFile psiFile,
                                   final RearrangerSettings settings)
  {
    /**
     * Per instructions from IntelliJ, we have to commit any changes to the document to the Psi
     * tree.
     */
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    documentManager.commitDocument(document);
    final WaitableBoolean wb = new WaitableBoolean();
    if (psiFile != null &&
        isFileWritable(psiFile) &&
        psiFile.getName().endsWith(".java"))
    {
      LOG.debug("schedule rearranger task");
      final Runnable task = new rearrangerTask(project, psiFile, settings, document, wb);
      CommandProcessor.getInstance().executeCommand(project, task, "Rearrange", null);
    }
    try {
      LOG.debug("wait for rearranger task to complete.");
      wb.whenTrue();
    }
    catch (InterruptedException e) {
      e.printStackTrace();
    }
    LOG.debug("end execute");
  }

  public static boolean isFileWritable(PsiElement element) {
    VirtualFile file = element.getContainingFile().getVirtualFile();
    return file != null && file.isWritable();
  }

  public final void rearrangeDocument(final Project project,
                                      final PsiFile psiFile,
                                      final RearrangerSettings settings,
                                      final Document document)
  {
    LOG.debug("enter rearrangeDocument");
    new CommentUtil(settings); // create CommentUtil singleton
    final Parser p = new Parser(project, settings, psiFile);
    final List<ClassContentsEntry> outerClasses = p.parseOuterLevel();
    if (outerClasses.size() > 0) {
      final Mover m = new Mover(outerClasses, settings);
      final List<RuleInstance> resultRuleInstances = m.rearrangeOuterClasses();
      boolean rearrange = true;
      if (settings.isAskBeforeRearranging()) {
        FileStructurePopup fsp = new FileStructurePopup(settings, resultRuleInstances, psiFile);
        rearrange = fsp.displayRearrangement();
      }
      if (rearrange) {
        final Emitter e = new Emitter(psiFile, resultRuleInstances, document);
        e.emitRearrangedDocument();
      }
    }
    LOG.debug("respacing document");
    PsiDocumentManager.getInstance(project).commitDocument(document);
    Spacer spacer = new Spacer(psiFile, document, settings);
    if (spacer.respace()) {
      PsiDocumentManager.getInstance(project).commitDocument(document);
    }
    LOG.debug("exit rearrangeDocument");
  }

// -------------------------- INNER CLASSES --------------------------

  final class rearrangerTask implements Runnable {
    final Project            project;
    final PsiFile            psiFile;
    final RearrangerSettings settings;
    final Document           document;
    final WaitableBoolean    wb;

    public rearrangerTask(final Project project,
                          final PsiFile psiFile,
                          final RearrangerSettings settings,
                          final Document document,
                          final WaitableBoolean wb)
    {
      this.project = project;
      this.psiFile = psiFile;
      this.settings = settings;
      this.document = document;
      this.wb = wb;
    }

    public final void run() {
      try {
        rearrangeDocument(project, psiFile, settings, document);
      }
      finally {
        wb.set();
      }
    }
  }
}

package com.intellij.debugger.impl;

import com.intellij.Patches;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.util.ui.MessageCategory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.progress.util.SmoothProgressAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IJSwingUtilities;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.sun.jdi.ReferenceType;

/**
 * User: lex
 * Date: Nov 18, 2003
 * Time: 2:20:18 PM
 */
public abstract class HotSwapProgress {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.HotSwapProgress");
  private final Project myProject;
  private Runnable myCancelWorker;
  private boolean myIsCancelled;

  public HotSwapProgress(Project project) {
    myProject = project;
  }

  public Project getProject() {
    return myProject;
  }

  public abstract void addMessage(final int type, final String[] text, final VirtualFile file, final int line, final int column);

  public abstract void setText(String text);

  public abstract void setTitle(String title);

  public abstract void setFraction(double v);  

  void message(final ReferenceType reference, final String text, final int category) {
    IJSwingUtilities.invoke(new Runnable() {
      public void run() {
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        String message = text;
        VirtualFile file = null;
        int lineIndex = 0;

        if (reference != null) {
          String className = DebuggerUtilsEx.signatureToName(reference.signature());
          GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
          PsiClass cls = PsiManager.getInstance(getProject()).findClass(className, scope);
          if (cls != null) {
            file = cls.getContainingFile().getVirtualFile();
            lineIndex = StringUtil.offsetToLineNumber(cls.getContainingFile().getText(), cls.getTextOffset());
          }

          message = "Class " + DebuggerUtilsEx.signatureToName(reference.signature()) + " : " + text;
        }

        addMessage(
          category,
          new String[]{message},
          file, lineIndex + 1, 1);
      }
    });
  }

  void error(ReferenceType reference, String text) {
    message(reference, text, MessageCategory.ERROR);
  }

  void message(ReferenceType reference, String text) {
    message(reference, text, MessageCategory.INFORMATION);
  }

  public void setCancelWorker(Runnable cancel) {
    myCancelWorker = cancel;
  }

  public void cancel() {
    myIsCancelled = true;
    if(myCancelWorker != null) myCancelWorker.run();
  }

  public void finished() {    
  }

  public abstract void setDebuggerSession(DebuggerSession debuggerSession);

  public boolean isCancelled() {
    return myIsCancelled;
  }
}

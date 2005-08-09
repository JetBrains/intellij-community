package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerManagerListener;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.HotSwapFile;
import com.intellij.debugger.impl.HotSwapManager;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.compiler.CompilationStatusListener;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.containers.HashMap;
import com.intellij.psi.PsiDocumentManager;

import java.util.*;

/**
 * User: lex
 * Date: Oct 2, 2003
 * Time: 6:00:55 PM
 */
public class HotSwapUI implements ProjectComponent{
  private boolean myAskBeforeHotswap = true;
  private final Project myProject;
  private final CompilationStatusListener myListener = new CompilationStatusListener() {
    public void compilationFinished(boolean aborted, int errors, int warnings) {
      if (errors == 0 && !aborted) {
        final List<DebuggerSession> sessions = new ArrayList<DebuggerSession>();
        Collection<DebuggerSession> debuggerSessions = (DebuggerManagerEx.getInstanceEx(myProject)).getSessions();
        for (Iterator iterator = debuggerSessions.iterator(); iterator.hasNext();) {
          DebuggerSession debuggerSession = (DebuggerSession)iterator.next();
          if(debuggerSession.isAttached() && debuggerSession.getProcess().canRedefineClasses()) {
            sessions.add(debuggerSession);
          }
        }
        if (sessions.size() > 0) {
          hotSwapSessions(sessions);
        }
      }
    }
  };

  public HotSwapUI(final Project project, CompilerManager compilerManager, DebuggerManagerEx debuggerManager) {
    myProject = project;
    compilerManager.addCompilationStatusListener(myListener);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public String getComponentName() {
    return "HotSwapUI";
  }

  public void initComponent() {
  }

  public void disposeComponent() {

  }

  private void hotSwapSessions(final List<DebuggerSession> sessions) {
    final boolean askBeforeHotswap = myAskBeforeHotswap;
    myAskBeforeHotswap = true;
    
    // need this because search with PSI is perormed during hotswap
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    new Thread() {
      public void run() {
        final HashMap<DebuggerSession, HashMap<String, HotSwapFile>> modifiedClasses = getModifiedClasses(sessions);

        if(modifiedClasses.isEmpty()) {
          final Application application = ApplicationManager.getApplication();
          application.invokeLater(new Runnable() {
            public void run() {
              WindowManager.getInstance().getStatusBar(myProject).setInfo("Loaded classes are up to date. Nothing to reload.");
            }
          }, application.getDefaultModalityState());
          return;
        }

        final HashMap<DebuggerSession, HashMap<String, HotSwapFile>> classesToReload = new HashMap<DebuggerSession, HashMap<String, HotSwapFile>>();
        if(askBeforeHotswap) {
          String runHotswap = DebuggerSettings.getInstance().RUN_HOTSWAP_AFTER_COMPILE;

          if(DebuggerSettings.RUN_HOTSWAP_ALWAYS.equals(runHotswap)) {
            classesToReload.putAll(modifiedClasses);
          }
          else if (DebuggerSettings.RUN_HOTSWAP_NEVER.equals(runHotswap)) {

          }
          else {
            ApplicationManager.getApplication().invokeAndWait(new Runnable() {
              public void run() {
                RunHotswapDialog dialog = new RunHotswapDialog(myProject, sessions);
                dialog.show();

                if(dialog.isOK()) {
                  for (Iterator<DebuggerSession> iterator = dialog.getSessionsToReload().iterator(); iterator.hasNext();) {
                    DebuggerSession debuggerSession =  iterator.next();
                    classesToReload.put(debuggerSession,  modifiedClasses.get(debuggerSession));
                  }
                }
              }
            }, ApplicationManager.getApplication().getDefaultModalityState());
          }
        }
        else {
          classesToReload.putAll(modifiedClasses);
        }

        reloadModifiedClasses(classesToReload);

      }
    }.start();
  }

  private void reloadModifiedClasses(final HashMap<DebuggerSession, HashMap<String, HotSwapFile>> modifiedClasses) {
    final HotSwapProgressImpl[] reloadClassesProgress = new HotSwapProgressImpl[1];
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      public void run() {
        reloadClassesProgress[0] = new HotSwapProgressImpl(myProject);
      }
    }, ApplicationManager.getApplication().getDefaultModalityState());

    ProgressManager.getInstance().runProcess(new Runnable() {
      public void run() {
        final HotSwapProgressImpl swapProgress = reloadClassesProgress[0];
        HotSwapManager.reloadModifiedClasses(modifiedClasses, swapProgress);
        swapProgress.finished();
      }
    }, reloadClassesProgress[0].getProgressIndicator());
  }

  private HashMap<DebuggerSession, HashMap<String, HotSwapFile>> getModifiedClasses(final List<DebuggerSession> sessions) {
    final HashMap[] classes = new HashMap[1];

    final HotSwapProgressImpl[] swapProgress = new HotSwapProgressImpl[1];

    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      public void run() {
        swapProgress[0] = new HotSwapProgressImpl(myProject);
      }
    }, ApplicationManager.getApplication().getDefaultModalityState());
    ProgressManager.getInstance().runProcess(new Runnable() {
      public void run() {
        classes[0] = HotSwapManager.getModifiedClasses(sessions, swapProgress[0]);
        swapProgress[0].finished();
        
      }
    }, swapProgress[0].getProgressIndicator());

    return classes[0];

  }

  public void reloadChangedClasses(final DebuggerSession session, boolean compileBeforeHotswap) {
    dontAskHotswapAfterThisCompilation();
    if (compileBeforeHotswap) {
      CompilerManager.getInstance(session.getProject()).make(null);
    }
    else {
      if(session.isAttached()) {
        final List<DebuggerSession> sessions = new ArrayList<DebuggerSession>(1);
        sessions.add(session);
        hotSwapSessions(sessions);
      }
    }
  }

  public void dontAskHotswapAfterThisCompilation() {
    myAskBeforeHotswap = false;
  }

  public static HotSwapUI getInstance(Project project) {
    return project.getComponent(HotSwapUI.class);
  }
}

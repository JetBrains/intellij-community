package com.intellij.debugger.impl;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.HotSwapFile;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.MethodSignature;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.MessageCategory;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VMDisconnectedException;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Mar 10, 2004
 * Time: 7:12:57 PM
 * To change this template use File | Settings | File Templates.
 */
class ReloadClassesWorker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.ReloadClassesWorker");

  private final DebuggerSession  myDebuggerSession;
  private final HotSwapProgress  myProgress;

  public ReloadClassesWorker(DebuggerSession session, HotSwapProgress progress) {
    myDebuggerSession = session;
    myProgress = progress;
  }

  private DebugProcessImpl getDebugProcess() {
    return myDebuggerSession.getProcess();
  }

  private Project getProject() {
    return myDebuggerSession.getProject();
  }

  private void reportObsoleteFrames(final Map<ThreadReferenceProxyImpl, PsiMethod[]> methodsOnTheStack) {
    Project project = getProject();
    PsiDocumentManager.getInstance(project).commitAndRunReadAction(new Runnable() {
        public void run() {
          VirtualMachineProxyImpl vm = getDebugProcess().getVirtualMachineProxy();
          for (Iterator it = vm.allThreads().iterator(); it.hasNext();) {
            try {
              ThreadReferenceProxyImpl threadProxy = (ThreadReferenceProxyImpl)it.next();
              if (threadProxy.isSuspended()) {
                PsiMethod[] methods = methodsOnTheStack.get(threadProxy);
                if (methods != null) {
                  for (Iterator itf = threadProxy.frames().iterator(); itf.hasNext();) {
                    StackFrameProxyImpl stackFrame = (StackFrameProxyImpl)itf.next();
                    if (stackFrame.location().method().isObsolete()) {
                      PsiMethod method = methods[stackFrame.getFrameIndex()];

                      if(method != null) {
                        PsiFile psiFile = method.getContainingFile();
                        VirtualFile file = null;
                        int lineIndex = 0;

                        if (psiFile != null) {
                          file = psiFile.getVirtualFile();
                          if(file != null) {
                            lineIndex = StringUtil.offsetToLineNumber(psiFile.getText(), method.getTextOffset());
                          }

                          MethodSignature sig = method.getSignature(PsiSubstitutor.EMPTY);
                          myProgress.addMessage(MessageCategory.WARNING, new String[]{sig + " : Breakpoints will be ignored for the obsolete version of the method. "},
                                                   file, lineIndex + 1, 1);
                        }
                      }
                    }
                  }
                }
              }
            }
            catch (EvaluateException e) {
            }
            catch (VMDisconnectedException e) {
            }
          }
        }
      });
    }

  private Map<ThreadReferenceProxyImpl, PsiMethod[]> getMethodsOnTheStack() {
    final Map<ThreadReferenceProxyImpl, PsiMethod[]> myThreadsToMethods = new HashMap<ThreadReferenceProxyImpl, PsiMethod[]>();

    PsiDocumentManager.getInstance(getProject()).commitAndRunReadAction(new Runnable() {
      public void run() {
        VirtualMachineProxyImpl vm = getDebugProcess().getVirtualMachineProxy();
        try {
          for (Iterator it = vm.allThreads().iterator(); it.hasNext();) {
            ThreadReferenceProxyImpl threadProxy = (ThreadReferenceProxyImpl)it.next();
            if (threadProxy.isSuspended()) {
              List frames = threadProxy.frames();

              PsiMethod[] methods = new PsiMethod[frames.size()];
              myThreadsToMethods.put(threadProxy, methods);
              for (Iterator itf = frames.iterator(); itf.hasNext();) {
                StackFrameProxyImpl stackFrame = (StackFrameProxyImpl)itf.next();
                methods[stackFrame.getFrameIndex()] = findPsiMethod(getProject(), stackFrame);
              }
            }
          }
        }
        catch (EvaluateException e) {
        }
      }
    });

    return myThreadsToMethods;
  }

  private PsiMethod findPsiMethod(Project project, StackFrameProxyImpl stackFrame) {
    try {
      String className = DebuggerUtilsEx.signatureToName(stackFrame.location().declaringType().signature());
      GlobalSearchScope scope = GlobalSearchScope.allScope(project);
      PsiClass cls = PsiManager.getInstance(project).findClass(className, scope);
      if(cls == null) return null;

      Method method = stackFrame.location().method();
      PsiMethod[] methods = cls.findMethodsByName(method.name(), false);
      nextMethod: for (int i = 0; i < methods.length; i++) {
        PsiMethod m = methods[i];
        if (method.isStatic() == m.hasModifierProperty(PsiModifier.STATIC)) {
          PsiParameter[] params = m.getParameterList().getParameters();
          List argTypes = method.argumentTypeNames();
          if (argTypes.size() == params.length) {
            int j = 0;
            for (Iterator iterator = argTypes.iterator(); iterator.hasNext(); j++) {
              String typeName = (String)iterator.next();
              if (!params[j].getType().getCanonicalText().equals(typeName)) continue nextMethod;
            }
            return m;
          }
        }
      }
    }
    catch (EvaluateException e) {
      e.printStackTrace(); 
    }

    return null;
  }

  private void processException(ReferenceType reference, Throwable e) {
    if (e.getMessage() != null) {
      myProgress.error(reference, e.getMessage());
    }

    if (e instanceof ProcessCanceledException) {
      myProgress.message(null, "Operation canceled");
      return;
    }

    if (e instanceof UnsupportedOperationException) {
      myProgress.error(reference, "Operation not supported by VM");
    }
    else if (e instanceof NoClassDefFoundError) {
      myProgress.error(reference, "Class not found");
    }
    else if (e instanceof VerifyError) {
      myProgress.error(reference, "Verification error");
    }
    else if (e instanceof UnsupportedClassVersionError) {
      myProgress.error(reference, "Unsupported class version");
    }
    else if (e instanceof ClassFormatError) {
      myProgress.error(reference, "Class format error");
    }
    else if (e instanceof ClassCircularityError) {
      myProgress.error(reference, "Class circularity error");
    }
    else {
      myProgress.error(reference, "Exception while reloading classes : " + e.getClass().getName());
    }
  }

  private byte[] loadFile(VirtualFile file) {
    try {
      return file.contentsToByteArray();
    }
    catch (IOException e) {
      return null;
    }
  }

  public void reloadClasses(final HashMap<String, HotSwapFile> modifiedClasses) {
    if(modifiedClasses == null) {
      myProgress.addMessage(MessageCategory.INFORMATION, new String[] { "Loaded classes are up to date. Nothing to reload." }, null, -1, -1);
      return;
    }

    VirtualMachineProxyImpl virtualMachineProxy = getDebugProcess().getVirtualMachineProxy();
    if(virtualMachineProxy == null) return;

    virtualMachineProxy.suspend();

    final Project project = getDebugProcess().getProject();
    try {
      Map<ThreadReferenceProxyImpl, PsiMethod[]> methodsOnTheStack = getMethodsOnTheStack();

      Map redefineMap = new HashMap();
      int classN = 0;
      for (Iterator iterator = modifiedClasses.keySet().iterator(); iterator.hasNext();) {
        classN++;
        String qualifiedName = (String)iterator.next();
        if (qualifiedName != null) {
          myProgress.setText(qualifiedName);
          myProgress.setFraction(classN / (double)modifiedClasses.size());
        }

        final HotSwapFile fileDescr = modifiedClasses.get(qualifiedName);

        //[max]: Generic enabled Computable<byte[]> confuses degenerator.
        byte[] buffer = (byte[])ApplicationManager.getApplication().runReadAction(new Computable() {
          public Object compute() {
            return loadFile(fileDescr.file);
          }
        });

        redefineMap.clear();
        List classes = virtualMachineProxy.classesByName(qualifiedName);
        for (Iterator i = classes.iterator(); i.hasNext();) {
          ReferenceType reference = (ReferenceType)i.next();

          if (buffer == null) {
            myProgress.error(reference, "I/O error");
          }
          redefineMap.put(reference, buffer);
        }
        getDebugProcess().getVirtualMachineProxy().redefineClasses(redefineMap);
        //myProgress.addMessage(MessageCategory.INFORMATION, new String[] { qualifiedName + " reloaded" }, null, -1, -1);
      }
      myProgress.setFraction(1);
      myProgress.message(null,
                            modifiedClasses.size() + " class" + (modifiedClasses.size() == 1 ? "" : "es") +
                            " reloaded.");
      if (LOG.isDebugEnabled()) {
        LOG.debug("classes reloaded");
      }
      reportObsoleteFrames(methodsOnTheStack);
      if (LOG.isDebugEnabled()) {
        LOG.debug("obsolete frames reported");
      }
    }
    catch (Throwable e) {
      processException(null, e);
    }

    DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
      public void run() {
        (DebuggerManagerEx.getInstanceEx(project)).getBreakpointManager().reloadBreakpoints();
        (DebuggerManagerEx.getInstanceEx(project)).getBreakpointManager().updateAllRequests();
        if (LOG.isDebugEnabled()) {
          LOG.debug("requests updated");
          LOG.debug("time stamp set");
        }
        myDebuggerSession.refresh();

        getDebugProcess().getManagerThread().invokeLater(new DebuggerCommandImpl() {
          protected void action() throws Exception {
            try {
              getDebugProcess().getVirtualMachineProxy().resume();
            }
            catch (Exception e) {
              processException(null, e);
            }
          }
        });
      }
    });
  }
}

package com.intellij.debugger.engine;

import com.intellij.debugger.*;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.request.ClassPrepareRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 2, 2004
 * Time: 8:33:41 PM
 * To change this template use File | Settings | File Templates.
 */
public class PositionManagerImpl implements PositionManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.PositionManagerImpl");

  private final DebugProcessImpl myDebugProcess;

  public PositionManagerImpl(DebugProcessImpl debugProcess) {
    myDebugProcess = debugProcess;
  }

  public DebugProcess getDebugProcess() {
    return myDebugProcess;
  }

  public List<Location> locationsOfLine(ReferenceType type,
                                        SourcePosition position) {
    try {
      int line = position.getLine() + 1;
      List<Location> locs = (List<Location>) (getDebugProcess().getVirtualMachineProxy().versionHigher("1.4") ? type.locationsOfLine(DebugProcessImpl.JAVA_STRATUM, null, line) : type.locationsOfLine(line));
      if (locs.size() > 0) {
        return locs;
      }
    }
    catch (AbsentInformationException e) {
    }

    return new ArrayList<Location>();
  }

  public ClassPrepareRequest createPrepareRequest(final ClassPrepareRequestor requestor, final SourcePosition position) {
    PsiClass psiClass = JVMNameUtil.getClassAt(position);
    if(psiClass == null) {
      return null;
    }

    String waitPrepareFor;
    ClassPrepareRequestor waitRequestor;

    if(PsiUtil.isLocalOrAnonymousClass(psiClass)) {
      PsiClass parent = JVMNameUtil.getTopLevelParentClass(psiClass);

      if(parent == null) {
        return null;
      }

      waitPrepareFor = JVMNameUtil.getNonAnonymousClassName(parent) + "$*";

      waitRequestor = new ClassPrepareRequestor() {
        public void processClassPrepare(DebugProcess debuggerProcess, ReferenceType referenceType) {
          if (((DebugProcessImpl)debuggerProcess).getPositionManager().locationsOfLine(referenceType, position).size() > 0) {
            requestor.processClassPrepare(debuggerProcess, referenceType);
          }
        }
      };
    } else {
      waitPrepareFor = JVMNameUtil.getNonAnonymousClassName(psiClass);
      waitRequestor = requestor;
    }
    return myDebugProcess.getRequestsManager().createClassPrepareRequest(waitRequestor, waitPrepareFor);
  }

  public SourcePosition getSourcePosition(final Location location) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if(location == null) return null;

    PsiFile psiFile = getPsiFileByLocation(getDebugProcess().getProject(), location);
    if(psiFile == null ) return null;

    int     lineNumber  = calcLineIndex(psiFile, location);

    return SourcePosition.createFromLine(psiFile, lineNumber);
  }

  private int calcLineIndex(PsiFile psiFile,
                            Location location) {
    LOG.assertTrue(myDebugProcess != null);
    if (location == null) {
      return -1;
    }

    int lineNumber;
    try {
      lineNumber = location.lineNumber() - 1;
    }
    catch (InternalError e) {
      lineNumber = -1;
    }

    if (psiFile instanceof PsiCompiledElement || lineNumber < 0) {
      final String signature = location.method().signature();
      final String name = location.method().name();
      if(location.declaringType() == null) return -1;

      final String className = location.declaringType().name();

      if(name == null || signature == null) return -1;

      final PsiClass[] compiledClass = new PsiClass[1];
      final PsiMethod[] compiledMethod = new PsiMethod[1];

      PsiRecursiveElementVisitor visitor = new PsiRecursiveElementVisitor() {
        public void visitReferenceExpression(PsiReferenceExpression expression) {
          expression.acceptChildren(this);
        }

        public void visitClass(PsiClass aClass) {
          List<ReferenceType> allClasses = myDebugProcess.getPositionManager().getAllClasses(SourcePosition.createFromElement(aClass));
          for (Iterator<ReferenceType> iterator = allClasses.iterator(); iterator.hasNext();) {
            ReferenceType referenceType = iterator.next();
            if(referenceType.name().equals(className)) {
              compiledClass[0] = aClass;
            }
          }

          aClass.acceptChildren(this);
        }

        public void visitMethod(PsiMethod method) {
          try {
            String methodName = method.isConstructor() ? "<init>" : method.getName();
            PsiClass containingClass = method.getContainingClass();
            
            if(containingClass != null && containingClass.equals(compiledClass[0]) && name.equals(methodName) && JVMNameUtil.getJVMSignature(method).getName(myDebugProcess).equals(signature)) {
              compiledMethod[0] = method;
            }
          }
          catch (EvaluateException e) {
            LOG.debug(e);
          }
        }
      };
      psiFile.accept(visitor);
      if(compiledMethod [0] != null) {
        Document document = PsiDocumentManager.getInstance(myDebugProcess.getProject()).getDocument(psiFile);
        if(document != null){
          return document.getLineNumber(compiledMethod[0].getTextOffset());
        }
      }
      return -1;
    }

    return lineNumber;
  }

  private static PsiFile getPsiFileByLocation(final Project project,
                                              final Location location) {
    if (location == null) {
      return null;
    }
    final ReferenceType refType = location.declaringType();
    if (refType == null) {
      return null;
    }

    final String originalQName = refType.name();
    final PsiManager psiManager = PsiManager.getInstance(project);
    int dollar = originalQName.indexOf('$');
    final String qName = dollar >= 0 ? originalQName.substring(0, dollar) : originalQName;
    PsiClass psiClass = DebuggerUtils.findClass(qName, project);
    if (psiClass == null) {
      psiClass = psiManager.findClass(originalQName, GlobalSearchScope.allScope(project)); // try to lookup original name
    }
    
    if (psiClass != null) {
      psiClass = (PsiClass)psiClass.getNavigationElement();
      return psiClass.getContainingFile();
    }

    return null;
  }

  public List<ReferenceType> getAllClasses(final SourcePosition classPosition) {
    final PsiClass psiClass = JVMNameUtil.getClassAt(classPosition);

    if(psiClass == null) return (List<ReferenceType>)Collections.EMPTY_LIST;

    return ApplicationManager.getApplication().runReadAction(new Computable<List<ReferenceType>> () {
      public List<ReferenceType> compute() {
        if(PsiUtil.isLocalOrAnonymousClass(psiClass)) {
          PsiClass parentNonLocal = JVMNameUtil.getTopLevelParentClass(psiClass);
          if(parentNonLocal == null && psiClass != null) {
            LOG.assertTrue(false, "Local class has no non-local parent");
            return (List<ReferenceType>)Collections.EMPTY_LIST;
          }
          String name = JVMNameUtil.getNonAnonymousClassName(parentNonLocal);
          List<ReferenceType> outer = myDebugProcess.getVirtualMachineProxy().classesByName(name);
          return findNested(outer, classPosition);
        } else {
          String name = JVMNameUtil.getNonAnonymousClassName(psiClass);
          return myDebugProcess.getVirtualMachineProxy().classesByName(name);
        }
      }
    });
  }

  private List<ReferenceType> findNested(List<ReferenceType> outer, SourcePosition classPosition) {
    List<ReferenceType> result = new ArrayList<ReferenceType>();

    for (Iterator<ReferenceType> iterator = outer.iterator(); iterator.hasNext();) {
      ReferenceType referenceType = iterator.next();
      if(referenceType.isPrepared()) {
        result.addAll(findNested((List<ReferenceType>)referenceType.nestedTypes(), classPosition));

        try {
          if(referenceType.locationsOfLine(classPosition.getLine() + 1).size() > 0) {
            result.add(referenceType);
          }
        }
        catch (AbsentInformationException e) {
        }
      }
    }
    return result;
  }
}

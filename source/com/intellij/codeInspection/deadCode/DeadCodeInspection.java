/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 12, 2001
 * Time: 9:40:45 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */

package com.intellij.codeInspection.deadCode;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.util.RefFilter;
import com.intellij.codeInspection.util.XMLExportUtl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiNonJavaFileReferenceProcessor;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.util.containers.HashMap;
import com.intellij.util.text.CharArrayUtil;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;

public class DeadCodeInspection extends FilteringInspectionTool {
  public boolean ADD_MAINS_TO_ENTRIES = true;
  public boolean ADD_JUNIT_TO_ENTRIES = true;
  public boolean ADD_EJB_TO_ENTRIES = true;
  public boolean ADD_APPLET_TO_ENTRIES = true;
  public boolean ADD_SERVLET_TO_ENTRIES = true;
  public boolean ADD_NONJAVA_TO_ENTRIES = true;

  private final Project myProject;
  private HashSet<RefElement> myProcessedSuspicious = null;
  private int myPhase;
  private QuickFixAction[] myQuickFixActions;
  public static final String DISPLAY_NAME = "Unused declaration";
  private RefUnreferencedFilter myFilter;
  private DeadHTMLComposer myComposer;
  public static final String SHORT_NAME = "UnusedDeclaration";

  public DeadCodeInspection(Project project) {
    myProject = project;
    myQuickFixActions = new QuickFixAction[]{new PermanentDeleteAction(), new CommentOutBin(), new MoveToEntries()};
  }

  private class OptionsPanel extends JPanel {
    private JCheckBox myMainsCheckbox;
    private JCheckBox myJUnitCheckbox;
    private JCheckBox myEJBMethodsCheckbox;
    private JCheckBox myAppletToEntries;
    private JCheckBox myServletToEntries;
    private JCheckBox myNonJavaCheckbox;

    private OptionsPanel() {
      super(new GridBagLayout());
      GridBagConstraints gc = new GridBagConstraints();
      gc.weightx = 1;
      gc.weighty = 0;
      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.anchor = GridBagConstraints.NORTHWEST;

      myMainsCheckbox = new JCheckBox("Automatically add all void main(String args[]) methods to entry points");
      myMainsCheckbox.setSelected(ADD_MAINS_TO_ENTRIES);
      myMainsCheckbox.getModel().addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          boolean selected = myMainsCheckbox.isSelected();
          ADD_MAINS_TO_ENTRIES = selected;
        }
      });

      gc.gridy = 0;
      add(myMainsCheckbox, gc);

      myEJBMethodsCheckbox = new JCheckBox("Automatically add all EJB interface methods to entry points");
      myEJBMethodsCheckbox.setSelected(ADD_EJB_TO_ENTRIES);
      myEJBMethodsCheckbox.getModel().addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          boolean selected = myEJBMethodsCheckbox.isSelected();
          ADD_EJB_TO_ENTRIES = selected;
        }
      });
      gc.gridy++;
      add(myEJBMethodsCheckbox, gc);

      myJUnitCheckbox = new JCheckBox("Automatically add all JUnit testcases to entry points");
      myJUnitCheckbox.setSelected(ADD_JUNIT_TO_ENTRIES);
      myJUnitCheckbox.getModel().addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          boolean selected = myJUnitCheckbox.isSelected();
          ADD_JUNIT_TO_ENTRIES = selected;
        }
      });

      gc.gridy++;
      add(myJUnitCheckbox, gc);

      myAppletToEntries = new JCheckBox("Automatically add all applets to entry points");
      myAppletToEntries.setSelected(ADD_APPLET_TO_ENTRIES);
      myAppletToEntries.getModel().addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          boolean selected = myAppletToEntries.isSelected();
          ADD_APPLET_TO_ENTRIES = selected;
        }
      });
      gc.gridy++;
      add(myAppletToEntries, gc);

      myServletToEntries = new JCheckBox("Automatically add all servlets to entry points");
      myServletToEntries.setSelected(ADD_SERVLET_TO_ENTRIES);
      myServletToEntries.getModel().addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          boolean selected = myServletToEntries.isSelected();
          ADD_SERVLET_TO_ENTRIES = selected;
        }
      });
      gc.gridy++;
      add(myServletToEntries, gc);

      myNonJavaCheckbox =
      new JCheckBox("Automatically add classes that have usages in non-java files to entry points");
      myNonJavaCheckbox.setSelected(ADD_NONJAVA_TO_ENTRIES);
      myNonJavaCheckbox.getModel().addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          boolean selected = myNonJavaCheckbox.isSelected();
          ADD_NONJAVA_TO_ENTRIES = selected;
        }
      });

      gc.gridy++;
      gc.weighty = 1;
      add(myNonJavaCheckbox, gc);
    }
  }

  public JComponent createOptionsPanel() {
    return new OptionsPanel();
  }

  private boolean isAddMainsEnabled() {
    return ADD_MAINS_TO_ENTRIES;
  }

  private boolean isAddJUnitEnabled() {
    return ADD_JUNIT_TO_ENTRIES;
  }

  private boolean isAddAppletEnabled() {
    return ADD_APPLET_TO_ENTRIES;
  }

  private boolean isAddServletEnabled() {
    return ADD_SERVLET_TO_ENTRIES;
  }

  private boolean isAddEjbInterfaceMethodsEnabled() {
    return ADD_EJB_TO_ENTRIES;
  }

  private boolean isAddNonJavaUsedEnabled() {
    return ADD_NONJAVA_TO_ENTRIES;
  }

  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  public String getGroupDisplayName() {
    return "";
  }

  public String getShortName() {
    return SHORT_NAME;
  }

  private static boolean isSerialVersionUIDField(PsiField field) {
    if (!"serialVersionUID".equals(field.getName())) return false;
    if (!field.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = field.getContainingClass();
    if (aClass != null && !isSerializable(aClass)) return false;
    return true;
  }

  private static boolean isWriteObjectMethod(PsiMethod method) {
    if (!"writeObject".equals(method.getName())) return false;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 1) return false;
    if (!parameters[0].getType().equalsToText("java.io.ObjectOutputStream")) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = method.getContainingClass();
    if (aClass != null && !isSerializable(aClass)) return false;
    return true;
  }

  private static boolean isReadObjectMethod(PsiMethod method) {
    if (!"readObject".equals(method.getName())) return false;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 1) return false;
    if (!parameters[0].getType().equalsToText("java.io.ObjectInputStream")) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = method.getContainingClass();
    if (aClass != null && !isSerializable(aClass)) return false;
    return true;
  }

  private static boolean isWriteReplaceMethod(PsiMethod method) {
    if (!"writeReplace".equals(method.getName())) return false;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 0) return false;
    if (!method.getReturnType().equalsToText("java.lang.Object")) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = method.getContainingClass();
    if (aClass != null && !isSerializable(aClass)) return false;
    return true;
  }

  private static boolean isReadResolveMethod(PsiMethod method) {
    if (!"readResolve".equals(method.getName())) return false;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 0) return false;
    if (!method.getReturnType().equalsToText("java.lang.Object")) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = method.getContainingClass();
    if (aClass != null && !isSerializable(aClass)) return false;
    return true;
  }

  private static boolean isSerializable(PsiClass aClass) {
    PsiClass serializableClass = aClass.getManager().findClass("java.io.Serializable", aClass.getResolveScope());
    if (serializableClass == null) return false;
    return aClass.isInheritor(serializableClass, true);
  }

  public void runInspection(AnalysisScope scope) {
    getRefManager().findAllDeclarations(); // Find all declaration elements.

    getRefManager().iterate(new RefManager.RefIterator() {
      public void accept(final RefElement refElement) {
        if (!refElement.isSuspicious()) return;
        refElement.accept(new RefVisitor() {
          public void visitMethod(RefMethod method) {
            if (isAddMainsEnabled() && method.isAppMain()) {
              getEntryPointsManager().addEntryPoint(method, false);
            }
          }

          public void visitClass(RefClass aClass) {
            if (isAddJUnitEnabled() && aClass.isTestCase()) {
              PsiClass psiClass = (PsiClass)aClass.getElement();
              addTestcaseEntries(psiClass);
            }
            else if (
              isAddAppletEnabled() && aClass.isApplet() ||
              isAddServletEnabled() && aClass.isServlet() ||
              aClass.isEjb()) {
              getEntryPointsManager().addEntryPoint(aClass, false);
            }
          }
        });
      }
    });

    if (isAddNonJavaUsedEnabled()) {
      checkForReachables();
      ProgressManager.getInstance().runProcess(new Runnable() {
        public void run() {
          final RefFilter filter = new StrictUnreferencedFilter();
          final PsiSearchHelper helper = PsiManager.getInstance(getRefManager().getProject()).getSearchHelper();
          getRefManager().iterate(new RefManager.RefIterator() {
            public void accept(final RefElement refElement) {
              if (refElement instanceof RefClass && filter.accepts(refElement)) {
                findExternalClassReferences((RefClass)refElement);
              }
              else if (refElement instanceof RefMethod) {
                RefMethod refMethod = (RefMethod)refElement;
                if (refMethod.isConstructor() && filter.accepts(refMethod)) {
                  findExternalClassReferences(refMethod.getOwnerClass());
                }
              }
            }

            private void findExternalClassReferences(final RefClass refElement) {
              PsiClass psiClass = (PsiClass)refElement.getElement();
              String qualifiedName = psiClass.getQualifiedName();
              if (qualifiedName != null) {
                helper.processUsagesInNonJavaFiles(qualifiedName,
                                                   new PsiNonJavaFileReferenceProcessor() {
                                                     public boolean process(PsiFile file, int startOffset, int endOffset) {
                                                       getEntryPointsManager().addEntryPoint(refElement, false);
                                                       return false;
                                                     }
                                                   },
                                                   GlobalSearchScope.projectScope(myProject));
              }
            }
          });
        }
      }, null);
    }

    myProcessedSuspicious = new HashSet<RefElement>();
    myPhase = 1;
  }

  private void addTestcaseEntries(PsiClass testClass) {
    RefClass refClass = (RefClass)getRefManager().getReference(testClass);
    getEntryPointsManager().addEntryPoint(refClass, false);
    PsiMethod[] testMethods = testClass.getMethods();
    for (int j = 0; j < testMethods.length; j++) {
      PsiMethod psiMethod = testMethods[j];
      if (psiMethod.hasModifierProperty(PsiModifier.PUBLIC) &&
          !psiMethod.hasModifierProperty(PsiModifier.ABSTRACT) &&
          psiMethod.getName().startsWith("test") || "suite".equals(psiMethod.getName())) {
        RefMethod refMethod = (RefMethod)getRefManager().getReference(psiMethod);
        getEntryPointsManager().addEntryPoint(refMethod, false);
      }
    }
  }

  private static class StrictUnreferencedFilter extends RefFilter {
    public int getElementProblemCount(RefElement refElement) {
      if (refElement instanceof RefParameter) return 0;
      if (refElement.isEntry() || !refElement.isSuspicious()) return 0;

      if (refElement instanceof RefField) {
        RefField refField = (RefField)refElement;
        if (refField.isUsedForReading() && !refField.isUsedForWriting()) return 1;
        if (refField.isUsedForWriting() && !refField.isUsedForReading()) return 1;
      }

      if (refElement instanceof RefClass && ((RefClass)refElement).isAnonymous()) return 0;
      return refElement.isReferenced() ? 0 : 1;
    }
  }

  public boolean queryExternalUsagesRequests() {
    checkForReachables();
    final RefFilter filter = myPhase == 1 ? (RefFilter)new StrictUnreferencedFilter() : new RefUnreachableFilter();
    final boolean[] requestAdded = new boolean[]{false};
    getRefManager().iterate(new RefManager.RefIterator() {
      public void accept(RefElement refElement) {
        if (refElement instanceof RefClass && ((RefClass)refElement).isAnonymous()) return;
        if (filter.accepts(refElement) && !myProcessedSuspicious.contains(refElement)) {
          refElement.accept(new RefVisitor() {
            public void visitField(final RefField refField) {
              myProcessedSuspicious.add(refField);
              PsiField psiField = (PsiField)refField.getElement();
              if (isSerialVersionUIDField(psiField)) {
                getEntryPointsManager().addEntryPoint(refField, false);
                return;
              }

              getManager().enqueueFieldUsagesProcessor(refField, new InspectionManagerEx.UsagesProcessor() {
                public boolean process(PsiReference psiReference) {
                  getEntryPointsManager().addEntryPoint(refField, false);
                  return false;
                }
              });
              requestAdded[0] = true;
            }

            public void visitMethod(final RefMethod refMethod) {
              myProcessedSuspicious.add(refMethod);
              if (refMethod instanceof RefImplicitConstructor) {
                visitClass(refMethod.getOwnerClass());
              }
              else {
                PsiMethod psiMethod = (PsiMethod)refMethod.getElement();
                if (isSerializablePatternMethod(psiMethod)) {
                  getEntryPointsManager().addEntryPoint(refMethod, false);
                  return;
                }

                if (!refMethod.isLibraryOverride() && refMethod.getAccessModifier() != PsiModifier.PRIVATE) {
                  for (Iterator<RefMethod> iterator = refMethod.getDerivedMethods().iterator(); iterator.hasNext();) {
                    myProcessedSuspicious.add(iterator.next());
                  }

                  if (isAddEjbInterfaceMethodsEnabled()) {
                    if (refMethod.isEjbDeclaration() || refMethod.isEjbImplementation()) {
                      addEjbMethodToEntries(refMethod);
                      return;
                    }
                  }

                  enqueueMethodUsages(refMethod);
                  requestAdded[0] = true;
                }
              }
            }

            public void visitClass(final RefClass refClass) {
              myProcessedSuspicious.add(refClass);
              if (refClass.isEjb()) {
                getEntryPointsManager().addEntryPoint(refClass, false);
              }
              else if (!refClass.isAnonymous()) {
                getManager().enqueueDerivedClassesProcessing(refClass, new InspectionManagerEx.DerivedClassesProcessor() {
                  public boolean process(PsiClass inheritor) {
                    getEntryPointsManager().addEntryPoint(refClass, false);
                    return false;
                  }
                });

                getManager().enqueueClassUsagesProcessing(refClass, new InspectionManagerEx.UsagesProcessor() {
                  public boolean process(PsiReference psiReference) {
                    getEntryPointsManager().addEntryPoint(refClass, false);
                    return false;
                  }
                });
                requestAdded[0] = true;
              }
            }
          });
        }
      }
    });

    if (!requestAdded[0]) {
      if (myPhase == 2) {
        myProcessedSuspicious = null;
        return false;
      }
      else {
        myPhase = 2;
      }
    }

    return true;
  }

  private static boolean isSerializablePatternMethod(PsiMethod psiMethod) {
    return isReadObjectMethod(psiMethod) || isWriteObjectMethod(psiMethod) || isReadResolveMethod(psiMethod) ||
           isWriteReplaceMethod(psiMethod);
  }

  private void addEjbMethodToEntries(RefMethod refMethod) {
    getEntryPointsManager().addEntryPoint(refMethod, false);
    for (Iterator<RefMethod> iterator = refMethod.getSuperMethods().iterator(); iterator.hasNext();) {
      RefMethod refSuper = iterator.next();
      addEjbMethodToEntries(refSuper);
    }
  }

  private void enqueueMethodUsages(final RefMethod refMethod) {
    if (refMethod.getSuperMethods().isEmpty()) {
      getManager().enqueueMethodUsagesProcessor(refMethod, new InspectionManagerEx.UsagesProcessor() {
        public boolean process(PsiReference psiReference) {
          getEntryPointsManager().addEntryPoint(refMethod, false);
          return false;
        }
      });
    }
    else {
      for (Iterator<RefMethod> iterator = refMethod.getSuperMethods().iterator(); iterator.hasNext();) {
        RefMethod refSuper = iterator.next();
        enqueueMethodUsages(refSuper);
      }
    }
  }

  public RefFilter getFilter() {
    if (myFilter == null) {
      myFilter = new RefUnreferencedFilter();
    }
    return myFilter;
  }

  public HTMLComposer getComposer() {
    if (myComposer == null) {
      myComposer = new DeadHTMLComposer(this);
    }
    return myComposer;
  }

  public void exportResults(final org.jdom.Element parentNode) {
    final RefUnreferencedFilter filter = new RefUnreferencedFilter();
    final DeadHTMLComposer composer = new DeadHTMLComposer(this);

    checkForReachables();

    getRefManager().iterate(new RefManager.RefIterator() {
      public void accept(RefElement refElement) {
        if (filter.accepts(refElement)) {
          if (refElement instanceof RefImplicitConstructor) return;
          org.jdom.Element element = XMLExportUtl.createElement(refElement, parentNode, -1);
          org.jdom.Element problemClassElement = new org.jdom.Element("problem_class");
          problemClassElement.addContent("unused declaration");
          element.addContent(problemClassElement);

          org.jdom.Element descriptionElement = new org.jdom.Element("description");
          StringBuffer buf = new StringBuffer();
          composer.appendProblemSynopsis(refElement, buf);
          descriptionElement.addContent(buf.toString());
          element.addContent(descriptionElement);
        }
      }
    });
  }

  public QuickFixAction[] getQuickFixes() {
    return myQuickFixActions;
  }

  public JobDescriptor[] getJobDescriptors() {
    return new JobDescriptor[]{InspectionManagerEx.BUILD_GRAPH, InspectionManagerEx.FIND_EXTERNAL_USAGES};
  }

  private void commentOutDead(PsiElement psiElement) {
    PsiFile psiFile = psiElement.getContainingFile();

    if (psiFile != null) {
      Document doc = PsiDocumentManager.getInstance(myProject).getDocument(psiFile);
      TextRange textRange = psiElement.getTextRange();
      SimpleDateFormat format = new SimpleDateFormat();
      String date = format.format(new Date());

      int startOffset = textRange.getStartOffset();
      CharSequence chars = doc.getCharsSequence();
      while (CharArrayUtil.regionMatches(chars, startOffset, "// --Commented out by Inspection")) {
        int line = doc.getLineNumber(startOffset) + 1;
        if (line < doc.getLineCount()) {
          startOffset = doc.getLineStartOffset(line);
          startOffset = CharArrayUtil.shiftForward(chars, startOffset, " \t");
        }
      }

      int endOffset = textRange.getEndOffset();

      int line1 = doc.getLineNumber(startOffset);
      int line2 = doc.getLineNumber(endOffset - 1);

      if (line1 == line2) {
        doc.insertString(startOffset, "// --Commented out by Inspection (" + date + "): ");
      }
      else {
        for (int i = line1; i <= line2; i++) {
          doc.insertString(doc.getLineStartOffset(i), "//");
        }

        doc.insertString(doc.getLineStartOffset(Math.min(line2 + 1, doc.getLineCount() - 1)),
                         "// --Commented out by Inspection STOP (" + date + ")" + "\n");
        doc.insertString(doc.getLineStartOffset(line1), "// --Commented out by Inspection START (" + date + "):" + "\n");
      }
    }
  }

  private static EntryPointsManager getEntryPointsManager(Project project) {
    return EntryPointsManager.getInstance(project);
  }

  private class PermanentDeleteAction extends QuickFixAction {
    public PermanentDeleteAction() {
      super("Safe Delete", IconLoader.getIcon("/actions/cancel.png"), KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
            DeadCodeInspection.this);
    }

    protected boolean applyFix(RefElement[] refElements) {
      ArrayList<RefElement> deletedRefs = new ArrayList<RefElement>(1);
      final ArrayList<PsiElement> psiElements = new ArrayList<PsiElement>();
      for (int i = 0; i < refElements.length; i++) {
        RefElement refElement = refElements[i];
        PsiElement psiElement = refElement.getElement();
        if (psiElement == null) continue;
        psiElements.add(psiElement);
        RefUtil.removeRefElement(refElement, deletedRefs);
      }

      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          new SafeDeleteHandler().invoke(myProject, psiElements.toArray(new PsiElement[psiElements.size()]), false);
        }
      });

      return true;
    }
  }

  private class CommentOutBin extends QuickFixAction {
    public CommentOutBin() {
      super("Comment Out", null, KeyStroke.getKeyStroke(KeyEvent.VK_SLASH,
                                                        SystemInfo.isMac ? KeyEvent.META_MASK : KeyEvent.CTRL_MASK),
            DeadCodeInspection.this);
    }

    protected boolean applyFix(RefElement[] refElements) {
      ArrayList<RefElement> deletedRefs = new ArrayList<RefElement>(1);
      for (int i = 0; i < refElements.length; i++) {
        RefElement refElement = refElements[i];
        PsiElement psiElement = refElement.getElement();
        if (psiElement == null) continue;
        commentOutDead(psiElement);
        RefUtil.removeRefElement(refElement, deletedRefs);
      }

      EntryPointsManager entryPointsManager = getEntryPointsManager(myProject);
      for (int i = 0; i < deletedRefs.size(); i++) {
        RefElement refElement = deletedRefs.get(i);
        entryPointsManager.removeEntryPoint(refElement);
      }

      return true;
    }
  }

  private class MoveToEntries extends QuickFixAction {
    private MoveToEntries() {
      super("Add as Entry Point", null, KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0), DeadCodeInspection.this);
    }

    protected boolean applyFix(RefElement[] refElements) {
      for (int i = 0; i < refElements.length; i++) {
        RefElement refElement = refElements[i];
        EntryPointsManager.getInstance(getManager().getProject()).addEntryPoint(refElement, true);
      }

      return true;
    }
  }

  public void checkForReachables() {
    CodeScanner codeScanner = new CodeScanner();

    // Cleanup previous reachability information.
    getRefManager().iterate(new RefManager.RefIterator() {
      public void accept(RefElement refElement) {
        refElement.setReachable(false);
      }
    });


    SmartRefElementPointer[] entryPoints = getEntryPointsManager().getEntryPoints();
    for (int i = 0; i < entryPoints.length; i++) {
      SmartRefElementPointer entry = entryPoints[i];
      if (entry.getRefElement() != null) {
        processEntryPoint(entry.getRefElement(), codeScanner);
      }
    }

    while (codeScanner.newlyInstantiatedClassesCount() != 0) {
      codeScanner.cleanInstantiatedClassesCount();
      codeScanner.processDelayedMethods();
    }
  }

  private static class CodeScanner extends RefVisitor {
    private final HashMap<RefClass, HashSet<RefMethod>> myClassIDtoMethods;
    private final HashSet<RefClass> myInstantiatedClasses;
    private int myInstantiatedClassesCount;
    private final HashSet<RefMethod> myProcessedMethods;

    private CodeScanner() {
      myClassIDtoMethods = new HashMap<RefClass, HashSet<RefMethod>>();
      myInstantiatedClasses = new HashSet<RefClass>();
      myProcessedMethods = new HashSet<RefMethod>();
      myInstantiatedClassesCount = 0;
    }

    public void visitMethod(RefMethod method) {
      if (!myProcessedMethods.contains(method)) {
        // Process class's static intitializers
        if (method.isStatic() || method.isConstructor()) {
          if (method.isConstructor()) {
            addInstantiatedClass(method.getOwnerClass());
          }
          else {
            method.getOwnerClass().setReachable(true);
          }
          myProcessedMethods.add(method);
          makeContentReachable(method);
          makeClassInitializersReachable(method.getOwnerClass());
        }
        else {
          if (isClassInstantiated(method.getOwnerClass())) {
            myProcessedMethods.add(method);
            makeContentReachable(method);
          }
          else {
            addDelayedMethod(method);
          }

          for (Iterator<RefMethod> iterator = method.getDerivedMethods().iterator(); iterator.hasNext();) {
            RefMethod refSub = iterator.next();
            visitMethod(refSub);
          }
        }
      }
    }

    public void visitClass(RefClass refClass) {
      boolean alreadyActive = refClass.isReachable();
      refClass.setReachable(true);

      if (!alreadyActive) {
        // Process class's static intitializers.
        makeClassInitializersReachable(refClass);
      }

      addInstantiatedClass(refClass);
    }

    public void visitField(RefField field) {
      // Process class's static intitializers.
      if (!field.isReachable()) {
        makeContentReachable(field);
        makeClassInitializersReachable(field.getOwnerClass());
      }
    }

    private void addInstantiatedClass(RefClass refClass) {
      if (myInstantiatedClasses.add(refClass)) {
        refClass.setReachable(true);
        myInstantiatedClassesCount++;

        ArrayList<RefMethod> methods = refClass.getLibraryMethods();
        for (int i = 0; i < methods.size(); i++) {
          RefMethod refMethod = methods.get(i);
          refMethod.accept(this);
        }

        for (Iterator<RefClass> iterator = refClass.getBaseClasses().iterator(); iterator.hasNext();) {
          RefClass baseClass = iterator.next();
          addInstantiatedClass(baseClass);
        }
      }
    }

    private void makeContentReachable(RefElement refElement) {
      refElement.setReachable(true);
      for (Iterator<RefElement> iterator = refElement.getOutReferences().iterator(); iterator.hasNext();) {
        RefElement refCallee = iterator.next();
        refCallee.accept(this);
      }
    }

    private void makeClassInitializersReachable(RefClass refClass) {
      for (Iterator<RefElement> iterator = refClass.getOutReferences().iterator(); iterator.hasNext();) {
        RefElement refCallee = iterator.next();
        refCallee.accept(this);
      }
    }

    private void addDelayedMethod(RefMethod refMethod) {
      HashSet<RefMethod> methods = myClassIDtoMethods.get(refMethod.getOwnerClass());
      if (methods == null) {
        methods = new HashSet<RefMethod>();
        myClassIDtoMethods.put(refMethod.getOwnerClass(), methods);
      }
      methods.add(refMethod);
    }

    private boolean isClassInstantiated(RefClass refClass) {
      return myInstantiatedClasses.contains(refClass);
    }

    private int newlyInstantiatedClassesCount() {
      return myInstantiatedClassesCount;
    }

    private void cleanInstantiatedClassesCount() {
      myInstantiatedClassesCount = 0;
    }

    private void processDelayedMethods() {
      RefClass[] instClasses = myInstantiatedClasses.toArray(new RefClass[myInstantiatedClasses.size()]);
      for (int i = 0; i < instClasses.length; i++) {
        RefClass refClass = instClasses[i];
        if (isClassInstantiated(refClass)) {
          HashSet<RefMethod> methods = myClassIDtoMethods.get(refClass);
          if (methods != null) {
            RefMethod[] arMethods = methods.toArray(new RefMethod[methods.size()]);
            for (int j = 0; j < arMethods.length; j++) {
              arMethods[j].accept(this);
            }
          }
        }
      }
    }
  }

  private void processEntryPoint(RefElement refEntry, CodeScanner codeScanner) {
    refEntry.accept(codeScanner);
  }

  public void cleanup() {
    super.cleanup();
    getEntryPointsManager().cleanup();
  }

  private EntryPointsManager getEntryPointsManager() {
    return EntryPointsManager.getInstance(myProject);
  }

  public void updateContent() {
    checkForReachables();
    super.updateContent();
  }

  protected boolean shouldLoadElementCallees() {
    return true;
  }
}

package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefUtil;
import com.intellij.codeInspection.ui.InspectionPackageNode;
import com.intellij.codeInspection.ui.InspectionTreeNode;
import com.intellij.codeInspection.ui.ProblemDescriptionNode;
import com.intellij.codeInspection.ui.RefElementNode;
import com.intellij.codeInspection.util.XMLExportUtl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jdom.Element;

import java.util.*;

/**
 * @author max
 */
public abstract class DescriptorProviderInspection extends InspectionTool {
  private HashMap<RefElement, ProblemDescriptor[]> myProblemElements;
  private HashMap<String, Set<RefElement>> myPackageContents = null;
  private HashMap<ProblemDescriptor, RefElement> myProblemToElements;
  private DescriptorComposer myComposer;
  private HashMap<RefElement, Set<LocalQuickFix>> myQuickFixActions;


  protected DescriptorProviderInspection() {
    myProblemElements = new HashMap<RefElement, ProblemDescriptor[]>();
    myProblemToElements = new HashMap<ProblemDescriptor, RefElement>();
    myQuickFixActions = new HashMap<RefElement, Set<LocalQuickFix>>();
  }

  protected void addProblemElement(RefElement refElement, ProblemDescriptor[] descriptions) {
    if (refElement == null) return;
    if (descriptions == null || descriptions.length == 0) return;
    myProblemElements.put(refElement, descriptions);
    for (ProblemDescriptor description : descriptions) {
      myProblemToElements.put(description, refElement);
      final LocalQuickFix[] fixes = description.getFixes();
      if (fixes != null) {
        Set<LocalQuickFix> localQuickFixes = myQuickFixActions.get(refElement);
        if (localQuickFixes == null) {
          localQuickFixes = new java.util.HashSet<LocalQuickFix>();
          myQuickFixActions.put(refElement, localQuickFixes);
        }
        localQuickFixes.addAll(Arrays.asList(fixes));
      }
    }
  }

  protected void ignoreElement(RefElement refElement) {
    if (refElement == null) return;
    myProblemElements.remove(refElement);
    myQuickFixActions.remove(refElement);
  }

  public void ignoreProblem(ProblemDescriptor problem) {
    RefElement refElement = myProblemToElements.get(problem);
    if (refElement != null) ignoreProblem(refElement, problem, -1);
  }

  public void ignoreProblem(RefElement refElement, ProblemDescriptor problem, int idx) {
    if (refElement == null) return;
    final Set<LocalQuickFix> localQuickFixes = myQuickFixActions.get(refElement);
    final LocalQuickFix[] fixes = problem.getFixes();
    if (isIgnoreProblem(fixes, localQuickFixes, idx)){
      myProblemToElements.remove(problem);
      ProblemDescriptor[] descriptors = myProblemElements.get(refElement);
      if (descriptors != null) {
        ArrayList<ProblemDescriptor> newDescriptors = new ArrayList<ProblemDescriptor>(Arrays.asList(descriptors));
        newDescriptors.remove(problem);
        if (newDescriptors.size() > 0) {
          myProblemElements.put(refElement, newDescriptors.toArray(new ProblemDescriptor[newDescriptors.size()]));
        }
        else {
          myProblemElements.remove(refElement);
        }
      }
    }
    if (idx == -1){
      myQuickFixActions.put(refElement, null);
    } else {
      if (localQuickFixes != null && fixes != null){
        if (fixes.length > idx){
          localQuickFixes.remove(fixes[idx]);
        }
      }
    }
  }

  private boolean isIgnoreProblem(LocalQuickFix[] problemFixes, Set<LocalQuickFix> fixes, int idx){
    if (problemFixes == null || fixes == null) {
      return true;
    }
    if (problemFixes.length <= idx){
      return true;
    }
    for (LocalQuickFix fix : problemFixes) {
      if (fix != problemFixes[idx] && !fixes.contains(fix)){
        return false;
      }
    }
    return true;
  }

  public void cleanup() {
    super.cleanup();
    myProblemElements.clear();
    myProblemToElements.clear();
    myQuickFixActions.clear();
    myPackageContents = null;
  }

  public ProblemDescriptor[] getDescriptions(RefElement refElement) {
    if (!refElement.isValid()) {
      ignoreElement(refElement);
      return null;
    }

    return myProblemElements.get(refElement);
  }

  public HTMLComposer getComposer() {
    if (myComposer == null) {
      myComposer = new DescriptorComposer(this);
    }
    return myComposer;
  }

  public void exportResults(final Element parentNode) {
    final Project project = getManager().getProject();
    getRefManager().iterate(new RefManager.RefIterator() {
      public void accept(final RefElement refElement) {
        if (myProblemElements.containsKey(refElement)) {
          ProblemDescriptor[] descriptions = getDescriptions(refElement);
          for (int i = 0; i < descriptions.length; i++) {
            ProblemDescriptor description = descriptions[i];

            int line = description.getLineNumber();
            final String template = description.getDescriptionTemplate();
            final PsiElement psiElement = description.getPsiElement();
            final String text = psiElement.getText();
            String problemText = template.replaceAll("#ref", text.replaceAll("\\$", "\\\\\\$"));
            problemText = problemText.replaceAll(" #loc ", " ");

            Element element = XMLExportUtl.createElement(refElement, parentNode, line);
            Element problemClassElement = new Element("problem_class");
            problemClassElement.addContent(getDisplayName());
            element.addContent(problemClassElement);
            Element descriptionElement = new Element("description");
            descriptionElement.addContent(problemText);
            element.addContent(descriptionElement);
          }
        }
      }
    });
  }

  public boolean hasReportedProblems() {
    return myProblemElements.size() > 0;
  }

  public void updateContent() {
    myPackageContents = new HashMap<String, Set<RefElement>>();
    final Set<RefElement> elements = myProblemElements.keySet();
    for (RefElement element : elements) {
      String packageName = RefUtil.getPackageName(element);
      Set<RefElement> content = myPackageContents.get(packageName);
      if (content == null) {
        content = new HashSet<RefElement>();
        myPackageContents.put(packageName, content);
      }
      content.add(element);
    }
  }

  public InspectionTreeNode[] getContents() {
    List<InspectionTreeNode> content = new ArrayList<InspectionTreeNode>();
    Set<String> packages = myPackageContents.keySet();
    for (String p : packages) {
      InspectionPackageNode pNode = new InspectionPackageNode(p);
      Set<RefElement> elements = myPackageContents.get(p);
      for (RefElement refElement : elements) {
        final RefElementNode elemNode = new RefElementNode(refElement, false);
        pNode.add(elemNode);
        final ProblemDescriptor[] problems = myProblemElements.get(refElement);
        for (ProblemDescriptor problem : problems) {
          elemNode.add(new ProblemDescriptionNode(refElement, problem));
        }
      }
      content.add(pNode);
    }
    return content.toArray(new InspectionTreeNode[content.size()]);
  }

  public Map<String, Set<RefElement>> getPackageContent() {
    return myPackageContents;
  }

  public QuickFixAction[] getQuickFixes(final RefElement[] refElements) {
    if (refElements == null) return null;
    Map<Class, QuickFixAction> result = new java.util.HashMap<Class, QuickFixAction>();
    for (RefElement refElement : refElements) {
      final Set<LocalQuickFix> localQuickFixes = myQuickFixActions.get(refElement);
      if (localQuickFixes != null){
        for (LocalQuickFix fix : localQuickFixes) {
          final Class klass = fix.getClass();
          final QuickFixAction quickFixAction = result.get(klass);
          if (quickFixAction != null){
            try {
              String familyName = fix.getFamilyName();
              familyName = familyName != null && familyName.length() > 0 ? "\'" + familyName + "\'" : familyName;
              ((LocalQuickFixWrapper)quickFixAction).setText("Apply Fix " + familyName);
            }
            catch (AbstractMethodError e) {
              //for plugin compatibility
              ((LocalQuickFixWrapper)quickFixAction).setText("Apply Fix");
            }
          } else {
            LocalQuickFixWrapper quickFixWrapper = new LocalQuickFixWrapper(fix, this);
            result.put(fix.getClass(), quickFixWrapper);
          }
        }
      }
    }
    return result.values().isEmpty() ? null : result.values().toArray(new QuickFixAction[result.size()]);
  }

  protected RefElement getElement(ProblemDescriptor descriptor) {
    return myProblemToElements.get(descriptor);
  }

  public void ignoreProblem(final ProblemDescriptor descriptor, final LocalQuickFix fix) {
    RefElement refElement = myProblemToElements.get(descriptor);
    if (refElement != null) {
      final LocalQuickFix[] fixes = descriptor.getFixes();
      for (int i = 0; i < fixes.length; i++) {
        if (fixes[i] == fix){
          ignoreProblem(refElement, descriptor, i);
          return;
        }
      }
    }
  }
}

package com.intellij.codeInspection.ex;

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
  private QuickFixAction[] myQuickFixActions;


  protected DescriptorProviderInspection() {
    myProblemElements = new HashMap<RefElement, ProblemDescriptor[]>();
    myProblemToElements = new HashMap<ProblemDescriptor, RefElement>();
    myQuickFixActions = new QuickFixAction[]{new LocalQuickFixWrapper(this)};
  }

  protected void addProblemElement(RefElement refElement, ProblemDescriptor[] descriptions) {
    if (refElement == null) return;

    myProblemElements.put(refElement, descriptions);
    for (int i = 0; i < descriptions.length; i++) {
      ProblemDescriptor description = descriptions[i];
      myProblemToElements.put(description, refElement);
    }
  }

  protected void ignoreElement(RefElement refElement) {
    if (refElement == null) return;
    myProblemElements.remove(refElement);
  }

  public void ignoreProblem(ProblemDescriptor problem) {
    RefElement refElement = myProblemToElements.get(problem);
    if (refElement != null) ignoreProblem(refElement, problem);
  }

  public void ignoreProblem(RefElement refElement, ProblemDescriptor problem) {
    if (refElement == null) return;
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

  public void cleanup() {
    super.cleanup();
    myProblemElements.clear();
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
    for(Iterator<RefElement> iterator = elements.iterator(); iterator.hasNext(); ) {
      RefElement element = iterator.next();
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
    for (Iterator<String> iterator = packages.iterator(); iterator.hasNext();) {
      String p = iterator.next();
      InspectionPackageNode pNode = new InspectionPackageNode(p);
      Set<RefElement> elements = myPackageContents.get(p);
      for(Iterator<RefElement> iterator1 = elements.iterator(); iterator1.hasNext(); ) {
        RefElement refElement = iterator1.next();
        final RefElementNode elemNode = new RefElementNode(refElement, false);
        pNode.add(elemNode);
        final ProblemDescriptor[] problems = myProblemElements.get(refElement);
        if (problems.length > 1) {
          for (int i = 0; i < problems.length; i++) {
            ProblemDescriptor problem = problems[i];
            elemNode.add(new ProblemDescriptionNode(refElement, problem));
          }
        } else if (problems.length == 1) {
          elemNode.setProblem(problems[0]);
        }
      }
      content.add(pNode);
    }
    return content.toArray(new InspectionTreeNode[content.size()]);
  }

  public Map<String, Set<RefElement>> getPackageContent() {
    return myPackageContents;
  }

  public QuickFixAction[] getQuickFixes() {
    return myQuickFixActions;
  }

  protected RefElement getElement(ProblemDescriptor descriptor) {
    return myProblemToElements.get(descriptor);
  }
}

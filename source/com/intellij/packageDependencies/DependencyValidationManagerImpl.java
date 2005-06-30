package com.intellij.packageDependencies;

import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.List;

public class DependencyValidationManagerImpl extends DependencyValidationManager implements JDOMExternalizable {
  private List<DependencyRule> myRules = new ArrayList<DependencyRule>();
  private Project myProject;
  private ContentManager myContentManager;

  public DependencyValidationManagerImpl(Project project) {
    myProject = project;
  }

  public boolean hasRules() {
    return myRules.size() > 0;
  }

  public DependencyRule getViolatorDependencyRule(PsiFile from, PsiFile to) {
    for (int i = 0; i < myRules.size(); i++) {
      DependencyRule dependencyRule = myRules.get(i);
      if (dependencyRule.isForbiddenToUse(from, to)) return dependencyRule;
    }

    return null;
  }

  public DependencyRule[] getViolatorDependencyRules(PsiFile from, PsiFile to) {
    ArrayList<DependencyRule> result = new ArrayList<DependencyRule>();
      for (int i = 0; i < myRules.size(); i++) {
        DependencyRule dependencyRule = myRules.get(i);
        if (dependencyRule.isForbiddenToUse(from, to)){
          result.add(dependencyRule);
        }
      }
      return result.toArray(new DependencyRule[result.size()]);

    }


  public DependencyRule[] getAllRules() {
    return myRules.toArray(new DependencyRule[myRules.size()]);
  }

  public void removeAllRules() {
    myRules.clear();
  }

  public void addRule(DependencyRule rule) {
    myRules.add(rule);
  }

  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        myContentManager = PeerFactory.getInstance().getContentFactory().createContentManager(true, myProject);
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
        ToolWindow toolWindow = toolWindowManager.registerToolWindow(ToolWindowId.DEPENDENCIES,
                                                                     myContentManager.getComponent(),
                                                                     ToolWindowAnchor.BOTTOM);

        toolWindow.setIcon(IconLoader.getIcon("/general/toolWindowInspection.png"));
        new ContentManagerWatcher(toolWindow, myContentManager);
      }
    });
  }

  public void addContent(Content content) {    
    myContentManager.addContent(content);
    myContentManager.setSelectedContent(content);
    ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.DEPENDENCIES).activate(null);
  }

  public void closeContent(Content content) {
    myContentManager.removeContent(content);
  }

  public void projectClosed() {
    ToolWindowManager.getInstance(myProject).unregisterToolWindow(ToolWindowId.DEPENDENCIES);
  }

  public void initComponent() {}

  public void disposeComponent() {}

  public String getComponentName() {
    return "DependencyValidationManager";
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    super.readExternal(element);

    List rules = element.getChildren("deny_rule");
    for (int i = 0; i < rules.size(); i++) {
      DependencyRule rule = readRule((Element)rules.get(i));
      if (rule != null) {
        addRule(rule);
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
    super.writeExternal(element);

    for (int i = 0; i < myRules.size(); i++) {
      DependencyRule rule = myRules.get(i);
      Element ruleElement = writeRule(rule);
      if (ruleElement != null) {
        element.addContent(ruleElement);
      }
    }
  }

  private Element writeRule(DependencyRule rule) {
    NamedScope fromScope = rule.getFromScope();
    NamedScope toScope = rule.getToScope();
    if (fromScope == null || toScope == null) return null;
    Element ruleElement = new Element("deny_rule");
    ruleElement.setAttribute("from_scope", fromScope.getName());
    ruleElement.setAttribute("to_scope", toScope.getName());
    ruleElement.setAttribute("is_deny", rule.isDenyRule() ? "true" : "false");
    return ruleElement;
  }

  private DependencyRule readRule(Element ruleElement) {
    String fromScope = ruleElement.getAttributeValue("from_scope");
    String toScope = ruleElement.getAttributeValue("to_scope");
    String denyRule = ruleElement.getAttributeValue("is_deny");
    if (fromScope == null || toScope == null || denyRule == null) return null;
    return new DependencyRule(getScope(fromScope), getScope(toScope), denyRule.equals("true"));
  }
}
package com.intellij.packageDependencies;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.*;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class DependencyValidationManagerImpl extends DependencyValidationManager {
  private List<DependencyRule> myRules = new ArrayList<DependencyRule>();

  public boolean SKIP_IMPORT_STATEMENTS = false;

  private final Project myProject;
  private ContentManager myContentManager;
  @NonNls private static final String DENY_RULE_KEY = "deny_rule";
  @NonNls private static final String FROM_SCOPE_KEY = "from_scope";
  @NonNls private static final String TO_SCOPE_KEY = "to_scope";
  @NonNls private static final String IS_DENY_KEY = "is_deny";
  private NamedScope myProjectTestScope;
  private NamedScope myProjectProductionScope;
  private NamedScope myProblemsScope;
  private static final Icon SHARED_SCOPES = IconLoader.getIcon("/ide/sharedScope.png");

  public DependencyValidationManagerImpl(Project project) {
    myProject = project;
  }

  public NamedScope getProjectTestScope() {
    if (myProjectTestScope == null) {
      final ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
      myProjectTestScope = new NamedScope(IdeBundle.message("predefined.scope.tests.name"), new PackageSet() {
        public boolean contains(PsiFile file, NamedScopesHolder holder) {
          final VirtualFile virtualFile = file.getVirtualFile();
          return file.getProject() == myProject && virtualFile != null && index.isInTestSourceContent(virtualFile);
        }

        public PackageSet createCopy() {
          return this;
        }

        public String getText() {
          return PatternPackageSet.SCOPE_TEST+":*..*";
        }

        public int getNodePriority() {
          return 0;
        }
      });
    }
    return myProjectTestScope;
  }

  public NamedScope getProjectProductionScope() {
    if (myProjectProductionScope == null) {
      final ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
      myProjectProductionScope = new NamedScope(IdeBundle.message("predefined.scope.production.name"), new PackageSet() {
        public boolean contains(PsiFile file, NamedScopesHolder holder) {
          final VirtualFile virtualFile = file.getVirtualFile();
          return file.getProject() == myProject
                 && virtualFile != null
                 && !index.isInTestSourceContent(virtualFile)
                 && !index.isInLibraryClasses(virtualFile)
                 && !index.isInLibrarySource(virtualFile)
            ;
        }

        public PackageSet createCopy() {
          return this;
        }

        public String getText() {
          return PatternPackageSet.SCOPE_SOURCE+":*..*";
        }

        public int getNodePriority() {
          return 0;
        }
      });
    }
    return myProjectProductionScope;
  }

  public NamedScope getProblemsScope() {
    if (myProblemsScope == null) {
      myProblemsScope = new NamedScope(IdeBundle.message("predefined.scope.problems.name"), new PackageSet() {
        public boolean contains(PsiFile file, NamedScopesHolder holder) {
          return file.getProject() == myProject && WolfTheProblemSolver.getInstance(myProject).isProblemFile(file.getVirtualFile());
        }

        public PackageSet createCopy() {
          return this;
        }

        public String getText() {
          return PatternPackageSet.SCOPE_PROBLEM + ":*..*";
        }

        public int getNodePriority() {
          return 1;
        }
      });
    }
    return myProblemsScope;
  }

  @NotNull
  public List<NamedScope> getPredefinedScopes() {
    final List<NamedScope> predifinedScopes = new ArrayList<NamedScope>();
    predifinedScopes.add(getProjectProductionScope());
    predifinedScopes.add(getProjectTestScope());
    predifinedScopes.add(getProblemsScope());
    final CustomScopesProvider[] scopesProviders = myProject.getExtensions(CustomScopesProvider.CUSTOM_SCOPES_PROVIDER);
    if (scopesProviders != null) {
      for (CustomScopesProvider scopesProvider : scopesProviders) {
        predifinedScopes.addAll(scopesProvider.getCustomScopes());
      }
    }
    return predifinedScopes;
  }

  public boolean hasRules() {
    return !myRules.isEmpty();
  }

  @Nullable
  public DependencyRule getViolatorDependencyRule(PsiFile from, PsiFile to) {
    for (DependencyRule dependencyRule : myRules) {
      if (dependencyRule.isForbiddenToUse(from, to)) return dependencyRule;
    }

    return null;
  }

  @NotNull
  public DependencyRule[] getViolatorDependencyRules(PsiFile from, PsiFile to) {
    ArrayList<DependencyRule> result = new ArrayList<DependencyRule>();
    for (DependencyRule dependencyRule : myRules) {
      if (dependencyRule.isForbiddenToUse(from, to)) {
        result.add(dependencyRule);
      }
    }
    return result.toArray(new DependencyRule[result.size()]);
  }

  public
  @NotNull
  DependencyRule[] getApplicableRules(PsiFile file) {
    ArrayList<DependencyRule> result = new ArrayList<DependencyRule>();
    for (DependencyRule dependencyRule : myRules) {
      if (dependencyRule.isApplicable(file)) {
        result.add(dependencyRule);
      }
    }
    return result.toArray(new DependencyRule[result.size()]);
  }

  public boolean skipImportStatements() {
    return SKIP_IMPORT_STATEMENTS;
  }

  public void setSkipImportStatements(final boolean skip) {
    SKIP_IMPORT_STATEMENTS = skip;
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
    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    if (toolWindowManager == null) return;
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        myContentManager = PeerFactory.getInstance().getContentFactory().createContentManager(true, myProject);
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
    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    if (toolWindowManager == null) return;
    toolWindowManager.unregisterToolWindow(ToolWindowId.DEPENDENCIES);
  }

  public void initComponent() {}

  public void disposeComponent() {}

  @NotNull
  public String getComponentName() {
    return "DependencyValidationManager";
  }

  public String getDisplayName() {
    return IdeBundle.message("shared.scopes.node.text");
  }

  public Icon getIcon() {
    return SHARED_SCOPES;
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    super.readExternal(element);

    List rules = element.getChildren(DENY_RULE_KEY);
    for (Object rule1 : rules) {
      DependencyRule rule = readRule((Element)rule1);
      if (rule != null) {
        addRule(rule);
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
    super.writeExternal(element);

    for (DependencyRule rule : myRules) {
      Element ruleElement = writeRule(rule);
      if (ruleElement != null) {
        element.addContent(ruleElement);
      }
    }
  }

  @Nullable
  private static Element writeRule(DependencyRule rule) {
    NamedScope fromScope = rule.getFromScope();
    NamedScope toScope = rule.getToScope();
    if (fromScope == null || toScope == null) return null;
    Element ruleElement = new Element(DENY_RULE_KEY);
    ruleElement.setAttribute(FROM_SCOPE_KEY, fromScope.getName());
    ruleElement.setAttribute(TO_SCOPE_KEY, toScope.getName());
    ruleElement.setAttribute(IS_DENY_KEY, Boolean.valueOf(rule.isDenyRule()).toString());
    return ruleElement;
  }

  @Nullable
  private DependencyRule readRule(Element ruleElement) {
    String fromScope = ruleElement.getAttributeValue(FROM_SCOPE_KEY);
    String toScope = ruleElement.getAttributeValue(TO_SCOPE_KEY);
    String denyRule = ruleElement.getAttributeValue(IS_DENY_KEY);
    if (fromScope == null || toScope == null || denyRule == null) return null;
    return new DependencyRule(getScope(fromScope), getScope(toScope), Boolean.valueOf(denyRule).booleanValue());
  }
}

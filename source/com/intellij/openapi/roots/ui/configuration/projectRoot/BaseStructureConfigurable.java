package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.facet.Facet;
import com.intellij.find.FindBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.TreeToolTipHandler;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Icons;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class BaseStructureConfigurable extends MasterDetailsComponent implements SearchableConfigurable, Disposable, Configurable.Assistant {

  private static final Icon FIND_ICON = IconLoader.getIcon("/actions/find.png");
  protected StructureConfigrableContext myContext;

  protected final Project myProject;

  protected boolean myUiDisposed = true;

  private boolean myWasTreeInitialized;

  protected BaseStructureConfigurable(final Project project) {
    myProject = project;
  }

  public void init(StructureConfigrableContext context) {
    myContext = context;
  }

  protected void initTree() {
    if (myWasTreeInitialized) return;
    myWasTreeInitialized = true;

    super.initTree();
    new TreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      public String convert(final TreePath treePath) {
        return ((MyNode)treePath.getLastPathComponent()).getDisplayName();
      }
    }, true);
    TreeToolTipHandler.install(myTree);
    ToolTipManager.sharedInstance().registerComponent(myTree);
    myTree.setCellRenderer(new ColoredTreeCellRenderer(){
      public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (value instanceof MyNode) {
          final MyNode node = (MyNode)value;

          if (node.getConfigurable() == null) {
            return;
          }

          final String displayName = node.getDisplayName();
          final Icon icon = node.getConfigurable().getIcon();
          setIcon(icon);
          setToolTipText(null);
          setFont(UIUtil.getTreeFont());
          if (node.isDisplayInBold()){
            append(displayName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          } else {
            final Object object = node.getConfigurable().getEditableObject();
            final boolean unused = myContext.isUnused(object, node);
            final boolean invalid = myContext.isInvalid(object);
            if (unused || invalid){
              Color fg = unused
                         ? UIUtil.getTextInactiveTextColor()
                         : selected && hasFocus ? UIUtil.getTreeSelectionForeground() : UIUtil.getTreeForeground();
              append(displayName, new SimpleTextAttributes(invalid ? SimpleTextAttributes.STYLE_WAVED : SimpleTextAttributes.STYLE_PLAIN,
                                                           fg,
                                                           Color.red));
              setToolTipText(composeTooltipMessage(invalid, object, displayName, unused));
            }
            else {
              append(displayName, selected && hasFocus ? SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
          }
        }
      }
    });

  }


  private String composeTooltipMessage(final boolean invalid, final Object object, final String displayName, final boolean unused) {
    final StringBuilder buf = StringBuilderSpinAllocator.alloc();
    try {
      if (invalid) {
        if (object instanceof Module) {
          final Module module = (Module)object;
          final Map<String, Set<String>> problems = myContext.myValidityCache.get(module);
          if (problems.containsKey(StructureConfigrableContext.NO_JDK)){
            buf.append(StructureConfigrableContext.NO_JDK).append("\n");
          }
          final Set<String> deletedLibraries = problems.get(StructureConfigrableContext.DELETED_LIBRARIES);
          if (deletedLibraries != null) {
            buf.append(ProjectBundle.message("project.roots.library.problem.message", deletedLibraries.size()));
            for (String problem : deletedLibraries) {
              if (deletedLibraries.size() > 1) {
                buf.append(" - ");
              }
              buf.append("\'").append(problem).append("\'").append("\n");
            }
          }
        } else {
          buf.append(ProjectBundle.message("project.roots.tooltip.library.misconfigured", displayName)).append("\n");
        }
      }
      if (unused) {
        buf.append(ProjectBundle.message("project.roots.tooltip.unused", displayName));
      }
      return buf.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(buf);
    }
  }

  public void disposeUIResources() {
    if (myUiDisposed) return;

    super.disposeUIResources();

    myUiDisposed = true;

    myAutoScrollHandler.cancelAllRequests();
    myContext.myUpdateDependenciesAlarm.cancelAllRequests();
    myContext.myUpdateDependenciesAlarm.addRequest(new Runnable(){
      public void run() {
        SwingUtilities.invokeLater(new Runnable(){
          public void run() {
            dispose();
          }
        });
      }
    }, 0);
  }

  protected class MyFindUsagesAction extends AnAction {
    public MyFindUsagesAction() {
      super(ProjectBundle.message("find.usages.action.text"), ProjectBundle.message("find.usages.action.text"), FIND_ICON);
      registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_USAGES).getShortcutSet(), myTree);
    }

    public void update(AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      final TreePath selectionPath = myTree.getSelectionPath();
      if (selectionPath != null){
        final MyNode node = (MyNode)selectionPath.getLastPathComponent();
        presentation.setEnabled(!node.isDisplayInBold());
      } else {
        presentation.setEnabled(false);
      }
    }

    public void actionPerformed(AnActionEvent e) {
      final Object selectedObject = getSelectedObject();
      final MyNode selectedNode = (MyNode)myTree.getSelectionPath().getLastPathComponent();
      final Set<String> dependencies = myContext.getCachedDependencies(selectedObject, selectedNode, true);
      if (dependencies == null || dependencies.isEmpty()) {
        Messages.showInfoMessage(myTree, FindBundle.message("find.usage.view.no.usages.text"),
                                 FindBundle.message("find.pointcut.applications.not.found.title"));
        return;
      }
      final int selectedRow = myTree.getSelectionRows()[0];
      final Rectangle rowBounds = myTree.getRowBounds(selectedRow);
      final Point location = rowBounds.getLocation();
      location.x += rowBounds.width;
      JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<String>(
        ProjectBundle.message("dependencies.used.in.popup.title"), dependencies.toArray(new String[dependencies.size()])) {

        public PopupStep onChosen(final String nameToSelect, final boolean finalChoice) {
          selectNodeInTree(nameToSelect);
          return PopupStep.FINAL_CHOICE;
        }

        public Icon getIconFor(String selection) {
          final Module module = myContext.myModulesConfigurator.getModule(selection);
          LOG.assertTrue(module != null, selection + " was not found");
          return module.getModuleType().getNodeIcon(false);
        }

      }).show(new RelativePoint(myTree, location));
    }
  }


  public void reset() {
    myUiDisposed = false;

    if (!myWasTreeInitialized) {
      initTree();
    }

    super.reset();
  }

  @NotNull
  protected ArrayList<AnAction> createActions(final boolean fromPopup) {
    final ArrayList<AnAction> result = new ArrayList<AnAction>();
    result.add(createAddAction());
    result.add(new MyRemoveAction());

    result.add(Separator.getInstance());

    result.add(new MyFindUsagesAction());


    return result;
  }

  protected abstract AbstractAddGroup createAddAction();

  protected class MyRemoveAction extends MyDeleteAction {
    public MyRemoveAction() {
      super(new Condition<Object>() {
        public boolean value(final Object object) {
          if (object instanceof MyNode) {
            final NamedConfigurable namedConfigurable = ((MyNode)object).getConfigurable();
            if (namedConfigurable != null) {
              final Object editableObject = namedConfigurable.getEditableObject();
              if (editableObject instanceof ProjectJdk || editableObject instanceof Module || editableObject instanceof Facet) return true;
              if (editableObject instanceof Library) {
                final LibraryTable table = ((Library)editableObject).getTable();
                return table == null || table.isEditable();
              }
            }
          }
          return false;
        }
      });
    }

    public void actionPerformed(AnActionEvent e) {
      final TreePath[] paths = myTree.getSelectionPaths();
      final Set<TreePath> pathsToRemove = new HashSet<TreePath>();
      for (TreePath path : paths) {
        if (removeFromModel(path)) {
          pathsToRemove.add(path);
        }
      }
      removePaths(pathsToRemove.toArray(new TreePath[pathsToRemove.size()]));
    }

    private boolean removeFromModel(final TreePath selectionPath) {
      final Object last = selectionPath.getLastPathComponent();

      if (!(last instanceof MyNode)) return false;

      final MyNode node = (MyNode)last;
      final NamedConfigurable configurable = node.getConfigurable();
      final Object editableObject = configurable.getEditableObject();
      if (editableObject instanceof ProjectJdk) {
        removeJdk((ProjectJdk)editableObject);
      }
      else if (editableObject instanceof Module) {
        if (removeModule((Module)editableObject)) return false;
      }
      else if (editableObject instanceof Facet) {
        if (removeFacet((Facet)editableObject)) return false;
      }
      else if (editableObject instanceof Library) {
        removeLibrary((Library)editableObject);
      }
      return true;
    }

  }


  protected void removeLibrary(Library library) {

  }

  protected boolean removeFacet(final Facet facet) {
    //if (!ProjectFileVersion.getInstance(myProject).isFacetDeletionEnabled(facet.getTypeId())) {
    //  return true;
    //}
    //getFacetConfigurator().removeFacet(facet);
    return false;
  }

  protected boolean removeModule(final Module module) {
    return false;
  }

  protected void removeJdk(final ProjectJdk editableObject) {
  }

  protected abstract class AbstractAddGroup extends ActionGroup implements MasterDetailsComponent.ActionGroupWithPreselection {

    protected AbstractAddGroup(String text, Icon icon) {
      super(text, true);

      final Presentation presentation = getTemplatePresentation();
      presentation.setIcon(icon);

      final Keymap active = KeymapManager.getInstance().getActiveKeymap();
      if (active != null) {
        final Shortcut[] shortcuts = active.getShortcuts("NewElement");
        setShortcutSet(new CustomShortcutSet(shortcuts));
      }
    }

    protected AbstractAddGroup(String text) {
      this(text, Icons.ADD_ICON);
    }

    public ActionGroup getActionGroup() {
      return this;
    }

    public int getDefaultIndex() {
        return 0;
      }
  }
  
}

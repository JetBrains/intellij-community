package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DataManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.impl.TypeSafeDataProviderAdapter;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.packaging.PackagingTreeParameters;
import com.intellij.openapi.roots.ui.configuration.projectRoot.FindUsagesInProjectStructureActionBase;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Disposer;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.CompositePackagingElementType;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TreeToolTipHandler;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class ArtifactsEditorImpl implements ArtifactsEditor {
  private JPanel myMainPanel;
  private JCheckBox myBuildOnMakeCheckBox;
  private TextFieldWithBrowseButton myOutputDirectoryField;
  private JPanel myToolbarPanel;
  private JCheckBox myShowIncludedCheckBox;
  private JPanel myEditorPanel;
  private Splitter mySplitter;
  private final Project myProject;
  private final PackagingTreeParameters myTreeParameters = new PackagingTreeParameters(false, false);
  private final EventDispatcher<ArtifactsEditorListener> myDispatcher = EventDispatcher.create(ArtifactsEditorListener.class);
  private final PackagingEditorContext myContext;
  private SourceItemsTree mySourceItemsTree;
  private final Artifact myOriginalArtifact;
  private final PackagingElementsTree myPackagingElementsTree;
  private TabbedPaneWrapper myTabbedPane;
  private ArtifactPostprocessingPanel myPostprocessingPanel;

  public ArtifactsEditorImpl(final PackagingEditorContext context, Artifact artifact) {
    myContext = context;
    myOriginalArtifact = artifact;
    myProject = context.getProject();
    mySourceItemsTree = new SourceItemsTree(myContext, this);
    myPackagingElementsTree = new PackagingElementsTree(this, myTreeParameters, myContext, myOriginalArtifact);
    myPostprocessingPanel = new ArtifactPostprocessingPanel(myContext);
    Disposer.register(this, mySourceItemsTree);
    Disposer.register(this, myPackagingElementsTree);
  }

  public void addListener(@NotNull final ArtifactsEditorListener listener) {
    myDispatcher.addListener(listener);
  }

  public PackagingEditorContext getContext() {
    return myContext;
  }

  public void removeListener(@NotNull final ArtifactsEditorListener listener) {
    myDispatcher.removeListener(listener);
  }

  public Artifact getArtifact() {
    return myContext.getArtifactModel().getModifiableOrOriginal(myOriginalArtifact);
  }

  public void rebuildTries() {
    myPackagingElementsTree.rebuildTree();
    mySourceItemsTree.rebuildTree();
    myPostprocessingPanel.updateProcessors(getArtifact());
  }


  public JComponent createMainComponent() {
    myMainPanel.putClientProperty(DataManager.CLIENT_PROPERTY_DATA_PROVIDER, new TypeSafeDataProviderAdapter(new MyDataProvider()));

    mySplitter = new Splitter(false);
    final JPanel leftPanel = new JPanel(new BorderLayout());
    leftPanel.add(myPackagingElementsTree.getTreePanel(), BorderLayout.CENTER);
    final Border border = BorderFactory.createEmptyBorder(3, 3, 3, 3);
    leftPanel.setBorder(border);
    mySplitter.setFirstComponent(leftPanel);

    final JPanel rightPanel = new JPanel(new BorderLayout());
    final JPanel rightTopPanel = new JPanel(new BorderLayout());
    rightTopPanel.add(new JLabel("Available Elements (drag'n'drop to layout tree)"), BorderLayout.SOUTH);
    rightPanel.add(rightTopPanel, BorderLayout.NORTH);
    rightPanel.add(ScrollPaneFactory.createScrollPane(mySourceItemsTree.getTree()), BorderLayout.CENTER);
    rightPanel.setBorder(border);
    mySplitter.setSecondComponent(rightPanel);


    myShowIncludedCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myTreeParameters.setShowIncludedContent(myShowIncludedCheckBox.isSelected());
        rebuildTries();
      }
    });

    final DefaultActionGroup toolbarActionGroup = new DefaultActionGroup();

    final List<AnAction> createActions2 = new ArrayList<AnAction>();
    AddCompositeElementActionGroup.addCompositeCreateActions(createActions2, this);
    for (AnAction createAction : createActions2) {
      toolbarActionGroup.add(createAction);
    }

    toolbarActionGroup.add(createAddAction(false));
    toolbarActionGroup.add(new RemovePackagingElementAction(this));
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarActionGroup, true);
    leftPanel.add(toolbar.getComponent(), BorderLayout.NORTH);
    rightTopPanel.setPreferredSize(new Dimension(-1, toolbar.getComponent().getPreferredSize().height));

    myTabbedPane = new TabbedPaneWrapper();
    myTabbedPane.addTab("Output Layout", mySplitter);
    myTabbedPane.addTab("Validation", myPostprocessingPanel.getMainPanel());
    myEditorPanel.add(myTabbedPane.getComponent(), BorderLayout.CENTER);

    DefaultActionGroup popupActionGroup = new DefaultActionGroup();
    final List<AnAction> createActions = new ArrayList<AnAction>();
    AddCompositeElementActionGroup.addCompositeCreateActions(createActions, this);
    for (AnAction createAction : createActions) {
      popupActionGroup.add(createAction);
    }
    popupActionGroup.add(createAddAction(true));
    final RemovePackagingElementAction removeAction = new RemovePackagingElementAction(this);
    removeAction.registerCustomShortcutSet(CommonShortcuts.DELETE, myPackagingElementsTree.getTreePanel());
    popupActionGroup.add(removeAction);
    popupActionGroup.add(new ExtractArtifactAction(this));
    popupActionGroup.add(new InlineArtifactAction(this));
    popupActionGroup.add(new RenameCompositeElementAction(this));
    popupActionGroup.add(new MyNavigateAction());
    popupActionGroup.add(new MyFindUsagesAction());

    popupActionGroup.add(Separator.getInstance());
    CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    DefaultTreeExpander treeExpander = new DefaultTreeExpander(myPackagingElementsTree.getTree());
    popupActionGroup.add(actionsManager.createExpandAllAction(treeExpander, myPackagingElementsTree.getTree()));
    popupActionGroup.add(actionsManager.createCollapseAllAction(treeExpander, myPackagingElementsTree.getTree()));

    PopupHandler.installPopupHandler(myPackagingElementsTree.getTree(), popupActionGroup, ActionPlaces.UNKNOWN, ActionManager.getInstance());
    TreeToolTipHandler.install(myPackagingElementsTree.getTree());
    ToolTipManager.sharedInstance().registerComponent(myPackagingElementsTree.getTree());
    rebuildTries();
    TreeUtil.expandAll(myPackagingElementsTree.getTree());
    return getMainComponent();
  }

  private AddPackagingElementActionGroup createAddAction(boolean popup) {
    return new AddPackagingElementActionGroup(this, popup);
  }

  public JComponent getMainComponent() {
    return myMainPanel;
  }

  private void navigate(PackagingElementNode treeNode) {
    treeNode.navigate(ModuleStructureConfigurable.getInstance(myProject));
  }

  public void addNewPackagingElement(@Nullable CompositePackagingElementType<?> parentType, @NotNull PackagingElementType<?> type) {
    myPackagingElementsTree.addNewPackagingElement(parentType, type);
  }

  public void removeSelectedElements() {
    myPackagingElementsTree.removeSelectedElements();
  }

  public boolean isModified() {
    if (getArtifact().getRootElement() == myOriginalArtifact.getRootElement()) return false;

    return true;//todo[nik]
  }

  public void dispose() {
  }

  public PackagingElementsTree getPackagingElementsTree() {
    return myPackagingElementsTree;
  }

  private class MyNavigateAction extends AnAction {
    private MyNavigateAction() {
      super(ProjectBundle.message("action.name.facet.navigate"));
      registerCustomShortcutSet(CommonShortcuts.getEditSource(), myPackagingElementsTree.getTree());
    }

    public void update(final AnActionEvent e) {
      PackagingElementNode[] treeNodes = myPackagingElementsTree.getSelectedNodes();
      e.getPresentation().setEnabled(treeNodes.length == 1 && treeNodes[0].canNavigate());
    }

    public void actionPerformed(final AnActionEvent e) {
      PackagingElementNode[] treeNodes = myPackagingElementsTree.getSelectedNodes();
      if (treeNodes.length == 1) {
        navigate(treeNodes[0]);
      }
    }
  }

  private class MyFindUsagesAction extends FindUsagesInProjectStructureActionBase {
    public MyFindUsagesAction() {
      super(myPackagingElementsTree.getTree(), myProject);
    }

    protected boolean isEnabled() {
      PackagingElementNode[] treeNodes = myPackagingElementsTree.getSelectedNodes();
      return treeNodes.length == 1 && treeNodes[0].getSourceObject() != null;
    }

    protected Object getSelectedObject() {
      PackagingElementNode[] treeNodes = myPackagingElementsTree.getSelectedNodes();
      return treeNodes.length == 1 ? treeNodes[0].getSourceObject() : null;
    }

    protected RelativePoint getPointToShowResults() {
      final int selectedRow = myPackagingElementsTree.getTree().getSelectionRows()[0];
      final Rectangle rowBounds = myPackagingElementsTree.getTree().getRowBounds(selectedRow);
      final Point location = rowBounds.getLocation();
      location.y += rowBounds.height;
      return new RelativePoint(myPackagingElementsTree.getTree(), location);
    }
  }

  private class MyDataProvider implements TypeSafeDataProvider {
    public void calcData(DataKey key, DataSink sink) {
      if (ARTIFACTS_EDITOR_KEY.equals(key)) {
        sink.put(ARTIFACTS_EDITOR_KEY, ArtifactsEditorImpl.this);
      }
    }
  }
}
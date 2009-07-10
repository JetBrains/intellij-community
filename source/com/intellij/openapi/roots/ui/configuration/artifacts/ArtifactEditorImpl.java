package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DataManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.impl.TypeSafeDataProviderAdapter;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.artifacts.actions.*;
import com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.SourceItemsTree;
import com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.LibrarySourceItem;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ModifiableArtifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.packaging.ui.ArtifactProblemQuickFix;
import com.intellij.packaging.ui.ArtifactValidationManager;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.TreeToolTipHandler;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ArtifactEditorImpl implements ArtifactEditorEx {
  private JPanel myMainPanel;
  private JCheckBox myBuildOnMakeCheckBox;
  private TextFieldWithBrowseButton myOutputDirectoryField;
  private JCheckBox myShowIncludedCheckBox;
  private JPanel myEditorPanel;
  private JCheckBox myClearOnRebuildCheckBox;
  private JPanel myErrorPanelPlace;
  private Splitter mySplitter;
  private final Project myProject;
  private final ComplexElementSubstitutionParameters mySubstitutionParameters = new ComplexElementSubstitutionParameters();
  private final EventDispatcher<ArtifactEditorListener> myDispatcher = EventDispatcher.create(ArtifactEditorListener.class);
  private final PackagingEditorContext myContext;
  private SourceItemsTree mySourceItemsTree;
  private final Artifact myOriginalArtifact;
  private final LayoutTreeComponent myLayoutTreeComponent;
  private TabbedPaneWrapper myTabbedPane;
  private ArtifactPropertiesEditors myPropertiesEditors;
  private ArtifactErrorPanel myErrorPanel;
  private ArtifactEditorImpl.ArtifactValidationManagerImpl myValidationManager;

  public ArtifactEditorImpl(final PackagingEditorContext context, Artifact artifact) {
    myContext = context;
    myOriginalArtifact = artifact;
    myProject = context.getProject();
    mySourceItemsTree = new SourceItemsTree(myContext, this);
    myLayoutTreeComponent = new LayoutTreeComponent(this, mySubstitutionParameters, myContext, myOriginalArtifact);
    myPropertiesEditors = new ArtifactPropertiesEditors(myContext, myOriginalArtifact);
    Disposer.register(this, mySourceItemsTree);
    Disposer.register(this, myLayoutTreeComponent);
    myBuildOnMakeCheckBox.setSelected(artifact.isBuildOnMake());
    myClearOnRebuildCheckBox.setSelected(artifact.isClearOutputDirectoryOnRebuild());
    final String outputPath = artifact.getOutputPath();
    myOutputDirectoryField.addBrowseFolderListener(CompilerBundle.message("dialog.title.output.directory.for.artifact"),
                                                   CompilerBundle.message("chooser.description.select.output.directory.for.0.artifact",
                                                                         getArtifact().getName()),
                                                   myProject, FileChooserDescriptorFactory.createSingleFolderDescriptor());
    setOutputPath(outputPath);
    myErrorPanel = new ArtifactErrorPanel(this);
    myValidationManager = new ArtifactValidationManagerImpl();
  }

  private void setOutputPath(@Nullable String outputPath) {
    myOutputDirectoryField.setText(outputPath != null ? FileUtil.toSystemDependentName(outputPath) : null);
  }

  public void apply() {
    final ModifiableArtifact modifiableArtifact = myContext.getModifiableArtifactModel().getOrCreateModifiableArtifact(myOriginalArtifact);
    modifiableArtifact.setBuildOnMake(myBuildOnMakeCheckBox.isSelected());
    modifiableArtifact.setClearOutputDirectoryOnRebuild(myClearOnRebuildCheckBox.isSelected());
    modifiableArtifact.setOutputPath(getConfiguredOutputPath());
    myPropertiesEditors.applyProperties();
  }

  @Nullable
  private String getConfiguredOutputPath() {
    String outputPath = FileUtil.toSystemIndependentName(myOutputDirectoryField.getText().trim());
    if (outputPath.length() == 0) {
      outputPath = null;
    }
    return outputPath;
  }

  public SourceItemsTree getSourceItemsTree() {
    return mySourceItemsTree;
  }

  public void addListener(@NotNull final ArtifactEditorListener listener) {
    myDispatcher.addListener(listener);
  }

  public PackagingEditorContext getContext() {
    return myContext;
  }

  public void removeListener(@NotNull final ArtifactEditorListener listener) {
    myDispatcher.removeListener(listener);
  }

  public Artifact getArtifact() {
    return myContext.getArtifactModel().getArtifactByOriginal(myOriginalArtifact);
  }

  public CompositePackagingElement<?> getRootElement() {
    return myLayoutTreeComponent.getRootElement();
  }

  public void rebuildTries() {
    myLayoutTreeComponent.rebuildTree();
    mySourceItemsTree.rebuildTree();
  }

  public void checkLayout() {
    myErrorPanel.clearError();
    getArtifact().getArtifactType().checkRootElement(getRootElement(), myValidationManager);
  }


  public JComponent createMainComponent() {
    mySourceItemsTree.initTree();
    myLayoutTreeComponent.initTree();
    myMainPanel.putClientProperty(DataManager.CLIENT_PROPERTY_DATA_PROVIDER, new TypeSafeDataProviderAdapter(new MyDataProvider()));

    myErrorPanelPlace.add(myErrorPanel.getMainPanel(), BorderLayout.CENTER);

    mySplitter = new Splitter(false);
    final JPanel leftPanel = new JPanel(new BorderLayout());
    leftPanel.add(myLayoutTreeComponent.getTreePanel(), BorderLayout.CENTER);
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
        if (myShowIncludedCheckBox.isSelected()) {
          mySubstitutionParameters.setSubstituteAll();
        }
        else {
          mySubstitutionParameters.setSubstituteNone();
        }
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
    myPropertiesEditors.addTabs(myTabbedPane);
    myEditorPanel.add(myTabbedPane.getComponent(), BorderLayout.CENTER);

    DefaultActionGroup popupActionGroup = new DefaultActionGroup();
    final List<AnAction> createActions = new ArrayList<AnAction>();
    AddCompositeElementActionGroup.addCompositeCreateActions(createActions, this);
    for (AnAction createAction : createActions) {
      popupActionGroup.add(createAction);
    }
    popupActionGroup.add(createAddAction(true));
    final RemovePackagingElementAction removeAction = new RemovePackagingElementAction(this);
    removeAction.registerCustomShortcutSet(CommonShortcuts.DELETE, myLayoutTreeComponent.getTreePanel());
    popupActionGroup.add(removeAction);
    popupActionGroup.add(new ExtractArtifactAction(this));
    popupActionGroup.add(new InlineArtifactAction(this));
    popupActionGroup.add(new RenameCompositeElementAction(this));
    popupActionGroup.add(Separator.getInstance());
    popupActionGroup.add(new HideContentAction(this));
    popupActionGroup.add(new ArtifactEditorNavigateAction(myLayoutTreeComponent));
    popupActionGroup.add(new ArtifactEditorFindUsagesAction(myLayoutTreeComponent, myProject));

    popupActionGroup.add(Separator.getInstance());
    CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    DefaultTreeExpander treeExpander = new DefaultTreeExpander(myLayoutTreeComponent.getLayoutTree());
    popupActionGroup.add(actionsManager.createExpandAllAction(treeExpander, myLayoutTreeComponent.getLayoutTree()));
    popupActionGroup.add(actionsManager.createCollapseAllAction(treeExpander, myLayoutTreeComponent.getLayoutTree()));

    PopupHandler.installPopupHandler(myLayoutTreeComponent.getLayoutTree(), popupActionGroup, ActionPlaces.UNKNOWN, ActionManager.getInstance());
    TreeToolTipHandler.install(myLayoutTreeComponent.getLayoutTree());
    ToolTipManager.sharedInstance().registerComponent(myLayoutTreeComponent.getLayoutTree());
    rebuildTries();
    return getMainComponent();
  }

  public ComplexElementSubstitutionParameters getSubstitutionParameters() {
    return mySubstitutionParameters;
  }

  private AddPackagingElementActionGroup createAddAction(boolean popup) {
    return new AddPackagingElementActionGroup(this);
  }

  public JComponent getMainComponent() {
    return myMainPanel;
  }

  public void addNewPackagingElement(@NotNull PackagingElementType<?> type) {
    myLayoutTreeComponent.addNewPackagingElement(type);
    mySourceItemsTree.rebuildTree();
  }

  public void removeSelectedElements() {
    myLayoutTreeComponent.removeSelectedElements();
  }

  public boolean isModified() {
    return myBuildOnMakeCheckBox.isSelected() != myOriginalArtifact.isBuildOnMake()
        || !Comparing.equal(getConfiguredOutputPath(), myOriginalArtifact.getOutputPath())
        || myClearOnRebuildCheckBox.isSelected() != myOriginalArtifact.isClearOutputDirectoryOnRebuild()
        || myPropertiesEditors.isModified();
  }

  public void dispose() {
  }

  public LayoutTreeComponent getLayoutTreeComponent() {
    return myLayoutTreeComponent;
  }

  public void updateOutputPath(@NotNull String oldArtifactName, @NotNull String newArtifactName) {
    final String oldDefaultPath = ArtifactUtil.getDefaultArtifactOutputPath(oldArtifactName, myProject);
    if (Comparing.equal(oldDefaultPath, getConfiguredOutputPath())) {
      setOutputPath(ArtifactUtil.getDefaultArtifactOutputPath(newArtifactName, myProject));
    }
  }

  public void putLibraryIntoDefaultLocation(@NotNull Library library) {
    myLayoutTreeComponent.putIntoDefaultLocations(Collections.singletonList(new LibrarySourceItem(library)));
  }

  private class MyDataProvider implements TypeSafeDataProvider {
    public void calcData(DataKey key, DataSink sink) {
      if (ARTIFACTS_EDITOR_KEY.equals(key)) {
        sink.put(ARTIFACTS_EDITOR_KEY, ArtifactEditorImpl.this);
      }
    }
  }

  private class ArtifactValidationManagerImpl implements ArtifactValidationManager {
    public PackagingEditorContext getContext() {
      return myContext;
    }

    public void registerProblem(@NotNull String messsage) {
      registerProblem(messsage, null);
    }

    public void registerProblem(@NotNull String messsage, @Nullable ArtifactProblemQuickFix quickFix) {
      myErrorPanel.showError(messsage, quickFix);
    }
  }
}
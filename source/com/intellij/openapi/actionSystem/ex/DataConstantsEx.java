package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.actionSystem.DataConstants;

public interface DataConstantsEx extends DataConstants {
  /**
   * Returns com.intellij.psi.PsiElement
   */
  String TARGET_PSI_ELEMENT = "psi.TargetElement";
  /**
   * Returns com.intellij.psi.PsiElement
   */
  String PASTE_TARGET_PSI_ELEMENT = "psi.pasteTargetElement";
  /**
   * Returns com.intellij.usageView.UsageView
   */
  String USAGE_VIEW = "usageView";
  /**
   * Returns com.intellij.codeInspection.ui.InsepctionResultsView
   */
  String INSPECTION_VIEW = "inspectionView";
  /**
   * Returns com.intellij.psi.PsiElement[]
   */
  String PSI_ELEMENT_ARRAY = "psi.Element.array";
  /**
   * Returns com.intellij.ide.CopyProvider
   */
  String COPY_PROVIDER = "copyProvider";
  /**
   * Returns com.intellij.ide.CutProvider
   */
  String CUT_PROVIDER = "cutProvider";
  /**
   * Returns com.intellij.ide.PasteProvider
   */
  String PASTE_PROVIDER = "pasteProvider";
  /**
   * Returns com.intellij.ide.IdeView
   */
  String IDE_VIEW = "IDEView";
  /**
   * Returns com.intellij.ide.DeleteProvider
   */
  String DELETE_ELEMENT_PROVIDER = "deleteElementProvider";
  /**
   * Returns TreeExpander
   */
  String TREE_EXPANDER = "treeExpander";
  /**
   * Returns ContentManager
   */
  String CONTENT_MANAGER = "contentManager";

  /**
   * Returns java.awt.Component
   */ 
  String CONTEXT_COMPONENT = "contextComponent";
  /**
   * Returns RuntimeConfiguration
   */
  String RUNTIME_CONFIGURATION = "runtimeConfiguration";

  /** Returns PsiElement */
  String SECONDARY_PSI_ELEMENT = "secondaryPsiElement";

  /**
   * Returns project file directory
   */
  String PROJECT_FILE_DIRECTORY = "context.ProjectFileDirectory";

  String MODALITY_STATE = "ModalityState";

  /**
   * returns Boolean
   */
  String SOURCE_NAVIGATION_LOCKED = "sourceNavigationLocked";
}

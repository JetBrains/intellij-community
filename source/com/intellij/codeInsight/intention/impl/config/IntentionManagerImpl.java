package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.daemon.impl.quickfix.PostIntentionsQuickFixAction;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.daemon.impl.quickfix.RemoveRedundantElseAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInsight.intention.impl.*;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiManager;

import java.util.ArrayList;
import java.util.List;

/**
 *  @author dsl
 */
public class IntentionManagerImpl extends IntentionManager {
  private List<IntentionAction> myActions = new ArrayList<IntentionAction>();
  private Project myProject;
  private IntentionManagerSettings mySettings;

  public IntentionManagerImpl(Project project, IntentionManagerSettings intentionManagerSettings) {
    myProject = project;
    mySettings = intentionManagerSettings;

    addAction(new QuickFixAction());
    addAction(new PostIntentionsQuickFixAction());

    registerIntentionAndMetaData(new SplitIfAction(), new String[]{"Control Flow"});
    registerIntentionAndMetaData(new InvertIfConditionAction(), new String[]{"Control Flow"});

    registerIntentionAndMetaData(new ImplementAbstractClassAction(), new String[]{"Declaration"});
    registerIntentionAndMetaData(new ImplementAbstractMethodAction(), new String[]{"Declaration"});
    registerIntentionAndMetaData(new SplitDeclarationAction(), new String[]{"Declaration"});
    //TODO:add metadata!
    addAction(new RemoveRedundantElseAction());

    registerIntentionAndMetaData(new EJBImplementationAction(), new String[]{"EJB"});
    registerIntentionAndMetaData(new EJBDeclarationAction(), new String[]{"EJB"});

  }

  public void registerIntentionAndMetaData(IntentionAction action, String[] category) {
    registerIntentionAndMetaData(action, category, action.getFamilyName());
  }
  public void registerIntentionAndMetaData(IntentionAction action, String[] category, String descriptionDirectoryName) {
    addAction(action);
    mySettings.registerIntentionMetaData(action, category, descriptionDirectoryName);
  }

  public void initComponent() { }

  public void disposeComponent(){
  }

  public String getComponentName(){
    return "IntentionManager";
  }

  public void projectOpened(){
    if (LanguageLevel.JDK_1_5.compareTo(PsiManager.getInstance(myProject).getEffectiveLanguageLevel()) <= 0) {
      registerIntentionAndMetaData(new MakeTypeGeneric(), new String[]{"Declaration"});
      registerIntentionAndMetaData(new AddOverrideAnnotationAction(), new String[]{"Declaration"}, "Add Override Annotation");

      registerIntentionAndMetaData(new AddOnDemandStaticImportAction(), new String[]{"Imports"});
      registerIntentionAndMetaData(new AddSingleMemberStaticImportAction(), new String[]{"Imports"});
    }
  }

  public void projectClosed(){
  }

  public void addAction(IntentionAction action) {
    myActions.add(action);
  }

  public IntentionAction[] getIntentionActions() {
    return myActions.toArray(new IntentionAction[myActions.size()]);
  }
}

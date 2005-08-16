package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.daemon.impl.quickfix.*;
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

    String[] CONTROL_FLOW_CAT = new String[]{"Control Flow"};
    registerIntentionAndMetaData(new SplitIfAction(), CONTROL_FLOW_CAT);
    registerIntentionAndMetaData(new InvertIfConditionAction(), CONTROL_FLOW_CAT);
    registerIntentionAndMetaData(new RemoveRedundantElseAction(), CONTROL_FLOW_CAT);

    String[] DECLARATION_CAT = new String[]{"Declaration"};
    registerIntentionAndMetaData(new CreateFieldFromParameterAction(), DECLARATION_CAT);
    registerIntentionAndMetaData(new AssignFieldFromParameterAction(), DECLARATION_CAT);
    registerIntentionAndMetaData(new CreateLocalVarFromInstanceofAction(), DECLARATION_CAT);
    registerIntentionAndMetaData(new ImplementAbstractClassAction(), DECLARATION_CAT);
    registerIntentionAndMetaData(new ImplementAbstractMethodAction(), DECLARATION_CAT);
    registerIntentionAndMetaData(new SplitDeclarationAction(), DECLARATION_CAT);
    registerIntentionAndMetaData(new AddRuntimeExceptionToThrowsAction(), DECLARATION_CAT);

    registerIntentionAndMetaData(new SimplifyBooleanExpressionAction(), "Boolean");

    registerIntentionAndMetaData(new EJBImplementationAction(), "EJB");
    registerIntentionAndMetaData(new EJBDeclarationAction(), "EJB");
  }

  public void registerIntentionAndMetaData(IntentionAction action, String... category) {
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

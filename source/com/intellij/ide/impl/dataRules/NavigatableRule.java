package com.intellij.ide.impl.dataRules;

import com.intellij.ide.DataManager;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;

import java.awt.*;

public class NavigatableRule implements GetDataRule {
  public Object getData(DataProvider dataProvider) {
    OpenFileDescriptor openFileDescriptor = (OpenFileDescriptor)dataProvider.getData(DataConstants.OPEN_FILE_DESCRIPTOR);
    if (openFileDescriptor != null) return openFileDescriptor;

    Project project = (Project)dataProvider.getData(DataConstants.PROJECT);
    if (project == null && dataProvider instanceof Component){
      project = (Project)DataManager.getInstance().getDataContext((Component)dataProvider).getData(DataConstants.PROJECT);
    }
    if (project == null) { return null; }

    PsiElement element = (PsiElement)dataProvider.getData(DataConstants.PSI_ELEMENT);
    if (element == null) return null;

    return EditSourceUtil.getDescriptor(element);
  }
}

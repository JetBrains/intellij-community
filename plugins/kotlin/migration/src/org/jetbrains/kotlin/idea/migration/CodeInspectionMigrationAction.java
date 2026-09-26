// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.migration;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static org.jetbrains.kotlin.idea.migration.KotlinMigrationProfileKt.createMigrationProfile;

public class CodeInspectionMigrationAction extends BaseAnalysisAction {
    private GlobalInspectionContextImpl myGlobalInspectionContext;
    private InspectionProfileImpl myExternalProfile;

    public CodeInspectionMigrationAction(@Nls String title, @Nls String analysisNoon) {
        super(title, analysisNoon);
    }

    @Override
    protected void analyze(@NotNull Project project, @NotNull AnalysisScope scope) {
        try {
            runInspections(project, scope);
        }
        finally {
            myGlobalInspectionContext = null;
            myExternalProfile = null;
        }
    }

    private void runInspections(Project project, AnalysisScope scope) {
        scope.setSearchInLibraries(false);

        FileDocumentManager.getInstance().saveAllDocuments();

        GlobalInspectionContextImpl inspectionContext = getGlobalInspectionContext(project);

        InspectionManagerEx managerEx = (InspectionManagerEx) InspectionManager.getInstance(project);
        myExternalProfile = createMigrationProfile(managerEx, null, null);

        inspectionContext.setExternalProfile(myExternalProfile);
        inspectionContext.setCurrentScope(scope);
        inspectionContext.doInspections(scope);
    }

    private GlobalInspectionContextImpl getGlobalInspectionContext(Project project) {
        if (myGlobalInspectionContext == null) {
            myGlobalInspectionContext = ((InspectionManagerEx) InspectionManager.getInstance(project)).createNewGlobalContext(false);
        }
        return myGlobalInspectionContext;
    }

    @Override
    protected @NonNls String getHelpTopic() {
        return "reference.dialogs.inspection.scope";
    }

    @Override
    protected void canceled() {
        super.canceled();
        myGlobalInspectionContext = null;
    }
}
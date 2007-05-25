package com.theoryinpractice.testng.configuration.browser;

import com.intellij.psi.PsiPackage;
import com.intellij.openapi.project.Project;
import com.intellij.execution.junit2.configuration.BrowseModuleValueActionListener;
import com.intellij.ide.util.PackageChooserDialog;

/**
 * @author Hani Suleiman
 *         Date: Jul 21, 2005
 *         Time: 12:56:02 PM
 */
public class PackageBrowser extends BrowseModuleValueActionListener
{
    public PackageBrowser(Project project) {
        super(project);
    }

    @Override
    protected String showDialog() {
        PackageChooserDialog chooser = new PackageChooserDialog("Choose Package", getProject());
        chooser.show();
        PsiPackage psiPackage = chooser.getSelectedPackage();
        String packageName = psiPackage == null ? null : psiPackage.getQualifiedName();
        return packageName;
    }

}

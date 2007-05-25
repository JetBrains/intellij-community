package com.theoryinpractice.testng.configuration.browser;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.execution.junit2.configuration.BrowseModuleValueActionListener;

/**
 * @author Hani Suleiman Date: Jul 21, 2005 Time: 12:56:02 PM
 */
public class SuiteBrowser extends BrowseModuleValueActionListener
{
    public SuiteBrowser(Project project) {
        super(project);
    }

    @Override
    protected String showDialog() {
        FileChooserDescriptor descriptor = new FileChooserDescriptor(true,
                                                                     false,
                                                                     false,
                                                                     false,
                                                                     false,
                                                                     false)
        {
            @Override
            public boolean isFileVisible(VirtualFile virtualFile, boolean showHidden) {
                if(!showHidden && virtualFile.getName().charAt(0) == '.') return false;
                return virtualFile.isDirectory() || "xml".equals(virtualFile.getExtension());
            }
        };
        descriptor.setDescription("Please select the testng.xml suite file");
        descriptor.setTitle("Select Suite");
        VirtualFile[] files = FileChooser.chooseFiles(getProject(), descriptor);
        if(files.length > 0) {
            return files[0].getPath();
        }
        return null;
    }

}

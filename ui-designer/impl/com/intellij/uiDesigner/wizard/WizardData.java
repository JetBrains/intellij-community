package com.intellij.uiDesigner.wizard;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.uiDesigner.lw.LwRootContainer;
import org.jetbrains.annotations.NotNull;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class WizardData {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.wizard.WizardData");

  @NotNull public final Project myProject;
  /**
   * Form's file.
   */
  @NotNull public final VirtualFile myFormFile;

  /**
   * If <code>true</code> then {@link #myShortClassName} and {@link #myPackageName} should be
   * used, otherwise {@link #myBeanClass} should be used.
   */
  public boolean myBindToNewBean;
  /**
   *
   */
  public String myShortClassName;
  /**
   *
   */
  public String myPackageName;

  /**
   * Bean's class. If <code>null</code> then bean's class is't defined yet.
   */
  public PsiClass myBeanClass;
  @NotNull public final FormProperty2BeanProperty[] myBindings;

  public boolean myGenerateIsModified;

  public WizardData(@NotNull final Project project, @NotNull final VirtualFile formFile) throws Generator.MyException {
    myProject = project;
    myFormFile = formFile;
    myBindToNewBean = true;
    myGenerateIsModified = true;

    final LwRootContainer[] rootContainer = new LwRootContainer[1];

    // Create initial bingings between form fields and bean's properties.
    // TODO[vova] ask Anton to not throw exception if form-field doesn't have corresponded field in the Java class
    final FormProperty[] formProperties = Generator.exposeForm(myProject, myFormFile, rootContainer);
    myBindings = new FormProperty2BeanProperty[formProperties.length];
    for(int i = formProperties.length - 1; i >= 0; i--){
      myBindings[i] = new FormProperty2BeanProperty(formProperties[i]);
    }

    final PsiManager manager = PsiManager.getInstance(myProject);
    final VirtualFile directory = formFile.getParent();
    LOG.assertTrue(directory.isDirectory());
    final PsiDirectory psiDirectory = manager.findDirectory(directory);
    LOG.assertTrue(psiDirectory != null);
    final PsiPackage aPackage = psiDirectory.getPackage();
    if(aPackage != null){
      myPackageName = aPackage.getQualifiedName();
    }
  }
}

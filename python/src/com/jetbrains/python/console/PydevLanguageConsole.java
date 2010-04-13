package com.jetbrains.python.console;

import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.PsiFileFactoryImpl;
import com.intellij.testFramework.LightVirtualFile;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.console.pydev.PydevConsoleCommunication;
import com.jetbrains.python.psi.impl.PyExpressionCodeFragmentImpl;

/**
 * @author oleg
 */
public class PydevLanguageConsole extends LanguageConsoleImpl {
  public PydevLanguageConsole(final Project project, final String title) {
    super(project, title, PythonLanguage.getInstance());
  }

  public void setPydevConsoleCommunication(final PydevConsoleCommunication communication) {
    myFile.putCopyableUserData(PydevConsoleRunner.CONSOLE_KEY, communication);
  }
}
package com.jetbrains.python.console;

import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.PsiFileFactoryImpl;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.FileContentUtil;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.impl.PyExpressionCodeFragmentImpl;

import java.util.Collections;
import java.util.List;

/**
 * @author oleg
 */
public class PyLanguageConsole extends LanguageConsoleImpl implements ConsoleNotification {
  private final LightVirtualFile myNewVFile;
  private final StringBuilder myStringBuilder;

  public PyLanguageConsole(final Project project, String title) {
    super(project, title, PythonLanguage.getInstance());
    myStringBuilder = new StringBuilder();
    myNewVFile = new LightVirtualFile("fake_console.py", PythonLanguage.getInstance(), myStringBuilder.toString());
  }

  public void inputSent(final String text) {
    myStringBuilder.append(text);
    myNewVFile.setContent(this, myStringBuilder.toString() + "PyCharmRulezzz", true);
    final PsiFile fakeFile = ((PsiFileFactoryImpl)PsiFileFactory.getInstance(getProject())).trySetupPsiForFile(myNewVFile, PythonLanguage.getInstance(), false, false);
    myFile.putCopyableUserData(PyExpressionCodeFragmentImpl.CONTEXT_KEY, fakeFile.getLastChild());
  }
}

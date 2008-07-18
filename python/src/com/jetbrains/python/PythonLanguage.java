package com.jetbrains.python;

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.impl.PyElementGeneratorImpl;
import com.jetbrains.python.validation.*;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * @author yole
 */
public class PythonLanguage extends Language {
  private final PyElementGenerator elementGenerator = new PyElementGeneratorImpl(this);
  private final IFileElementType ELTYPE_FILE = new IStubFileElementType(this);
  private final Set<Class<? extends PyAnnotator>> _annotators = new CopyOnWriteArraySet<Class<? extends PyAnnotator>>();

  public static PythonLanguage getInstance() {
    return (PythonLanguage) PythonFileType.INSTANCE.getLanguage();
  }

  {
    _annotators.add(AssignTargetAnnotator.class);
    _annotators.add(ParameterListAnnotator.class);
    _annotators.add(ArgumentListAnnotator.class);
    _annotators.add(ReturnAnnotator.class);
    _annotators.add(TryExceptAnnotator.class);
    _annotators.add(BreakContinueAnnotator.class);
    _annotators.add(GlobalAnnotator.class);
    _annotators.add(DocStringAnnotator.class);
    _annotators.add(ImportAnnotator.class);
    _annotators.add(UnresolvedReferenceAnnotator.class);
    _annotators.add(StringConstantAnnotator.class);
    _annotators.add(MethodParamsAnnotator.class);
    _annotators.add(DuplicateDeclAnnotator.class);
  }


  protected PythonLanguage() {
    super("Python");
  }

  public IFileElementType getFileElementType() {
    return ELTYPE_FILE;
  }

  public PyElementGenerator getElementGenerator() {
    return elementGenerator;
  }

  public PsiFile createDummyFile(Project project, String contents) {
    return PsiFileFactory.getInstance(project).createFileFromText("dummy." + PythonFileType.INSTANCE.getDefaultExtension(), contents);
  }

  public Set<Class<? extends PyAnnotator>> getAnnotators() {
    return _annotators;
  }
}

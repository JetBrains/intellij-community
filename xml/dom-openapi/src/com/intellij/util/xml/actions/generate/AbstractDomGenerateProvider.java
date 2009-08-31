package com.intellij.util.xml.actions.generate;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementNavigationProvider;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public abstract class AbstractDomGenerateProvider<T extends DomElement> extends DefaultGenerateElementProvider<T> {

  @Nullable private final String myMappingId;

  public AbstractDomGenerateProvider(final String description, final Class<T> aClass) {
    this(description, aClass, null);
  }

  public AbstractDomGenerateProvider(final String description, final Class<T> aClass, String mappingId) {
    super(description, aClass);
    myMappingId = mappingId;
  }

  public T generate(final Project project, final Editor editor, final PsiFile file) {
    final T t = super.generate(project, editor, file);

    runTemplate(editor, file, t);

    return t;
  }

  protected void runTemplate(final Editor editor, final PsiFile file, final T t) {
    DomTemplateRunner.getInstance(file.getProject()).runTemplate(t, myMappingId, editor);
  }

  protected abstract DomElement getParentDomElement(final Project project, final Editor editor, final PsiFile file);

  protected void doNavigate(final DomElementNavigationProvider navigateProvider, final DomElement copy) {
    final DomElement element = getElementToNavigate((T)copy);
    if (element != null) {
      super.doNavigate(navigateProvider, element);
    }
  }

  @Nullable
  protected DomElement getElementToNavigate(final T t) {
    return t;
  }

  protected static String getDescription(final Class<? extends DomElement> aClass) {
    return StringUtil.join(Arrays.asList(NameUtil.nameToWords(aClass.getSimpleName())), " ");
  }
}

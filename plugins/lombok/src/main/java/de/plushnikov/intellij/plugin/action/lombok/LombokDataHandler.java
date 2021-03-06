package de.plushnikov.intellij.plugin.action.lombok;

import com.intellij.psi.PsiClass;
import de.plushnikov.intellij.plugin.LombokClassNames;
import org.jetbrains.annotations.NotNull;

public class LombokDataHandler extends BaseLombokHandler {

  private final BaseLombokHandler[] handlers;

  public LombokDataHandler() {
    handlers = new BaseLombokHandler[]{
      new LombokGetterHandler(), new LombokSetterHandler(),
      new LombokToStringHandler(), new LombokEqualsAndHashcodeHandler()};
  }

  @Override
  protected void processClass(@NotNull PsiClass psiClass) {
    for (BaseLombokHandler handler : handlers) {
      handler.processClass(psiClass);
    }

    removeDefaultAnnotation(psiClass, LombokClassNames.GETTER);
    removeDefaultAnnotation(psiClass, LombokClassNames.SETTER);
    removeDefaultAnnotation(psiClass, LombokClassNames.TO_STRING);
    removeDefaultAnnotation(psiClass, LombokClassNames.EQUALS_AND_HASHCODE);
    addAnnotation(psiClass, LombokClassNames.DATA);
  }

}

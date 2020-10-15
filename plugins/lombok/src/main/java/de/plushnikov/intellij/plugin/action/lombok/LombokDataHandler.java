package de.plushnikov.intellij.plugin.action.lombok;

import com.intellij.psi.PsiClass;
import de.plushnikov.intellij.plugin.LombokNames;
import org.jetbrains.annotations.NotNull;

public class LombokDataHandler extends BaseLombokHandler {

  private final BaseLombokHandler[] handlers;

  public LombokDataHandler() {
    handlers = new BaseLombokHandler[]{
      new LombokGetterHandler(), new LombokSetterHandler(),
      new LombokToStringHandler(), new LombokEqualsAndHashcodeHandler()};
  }

  protected void processClass(@NotNull PsiClass psiClass) {
    for (BaseLombokHandler handler : handlers) {
      handler.processClass(psiClass);
    }

    removeDefaultAnnotation(psiClass, LombokNames.GETTER);
    removeDefaultAnnotation(psiClass, LombokNames.SETTER);
    removeDefaultAnnotation(psiClass, LombokNames.TO_STRING);
    removeDefaultAnnotation(psiClass, LombokNames.EQUALS_AND_HASHCODE);
    addAnnotation(psiClass, LombokNames.DATA);
  }

}

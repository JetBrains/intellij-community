package de.plushnikov.intellij.plugin.action.lombok;

import com.intellij.psi.PsiClass;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
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

    removeDefaultAnnotation(psiClass, Getter.class);
    removeDefaultAnnotation(psiClass, Setter.class);
    removeDefaultAnnotation(psiClass, ToString.class);
    removeDefaultAnnotation(psiClass, EqualsAndHashCode.class);

    addAnnotation(psiClass, Data.class);
  }

}

package de.plushnikov.intellij.plugin.inspection.modifiers;

import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.LombokClassNames;

import static com.intellij.psi.PsiModifier.FINAL;
import static com.intellij.psi.PsiModifier.STATIC;
import static de.plushnikov.intellij.plugin.inspection.modifiers.RedundantModifiersInfoType.*;

public class RedundantModifiersOnUtilityClassLombokAnnotationInspection extends LombokRedundantModifierInspection {

  public RedundantModifiersOnUtilityClassLombokAnnotationInspection() {
    super(
      LombokClassNames.UTILITY_CLASS,
      new RedundantModifiersInfo(CLASS, null, LombokBundle.message("inspection.message.utility.class.already.marks.class.final"), FINAL),
      new RedundantModifiersInfo(FIELD, null, LombokBundle.message("inspection.message.utility.class.already.marks.fields.static"), STATIC),
      new RedundantModifiersInfo(METHOD, null, LombokBundle.message("inspection.message.utility.class.already.marks.methods.static"), STATIC),
      new RedundantModifiersInfo(INNER_CLASS, null,
                                 LombokBundle.message("inspection.message.utility.class.already.marks.inner.classes.static"), STATIC)
    );
  }
}

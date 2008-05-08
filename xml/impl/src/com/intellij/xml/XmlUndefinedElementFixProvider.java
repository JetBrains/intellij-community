package com.intellij.xml;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public interface XmlUndefinedElementFixProvider {

  @NotNull IntentionAction[] createFixes(final @NotNull XmlElement element);
}

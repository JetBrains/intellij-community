package org.jetbrains.idea.maven.dom.beans;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.JavaNameStrategy;
import com.intellij.util.xml.NameStrategy;

@NameStrategy(JavaNameStrategy.class)
public interface MavenDomElement extends DomElement {
}

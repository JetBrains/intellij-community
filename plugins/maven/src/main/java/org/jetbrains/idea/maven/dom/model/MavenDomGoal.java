package org.jetbrains.idea.maven.dom.model;

import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.idea.maven.dom.converters.MavenPluginGoalConverter;

@Convert(MavenPluginGoalConverter.class)
public interface MavenDomGoal extends GenericDomValue<String> {

}

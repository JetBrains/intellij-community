// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.JdomKt;
import com.intellij.util.io.PathKt;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenArchetype;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class MavenArtifactManager {
  private static final String ELEMENT_ARCHETYPES = "archetypes";
  private static final String ELEMENT_ARCHETYPE = "archetype";
  private static final String ELEMENT_GROUP_ID = "groupId";
  private static final String ELEMENT_ARTIFACT_ID = "artifactId";
  private static final String ELEMENT_VERSION = "version";
  private static final String ELEMENT_REPOSITORY = "repository";
  private static final String ELEMENT_DESCRIPTION = "description";

  @NotNull
  public static List<MavenArchetype> loadUserArchetypes(@NotNull Path userArchetypesPath) {
    try {
      if (!PathKt.exists(userArchetypesPath)) {
        return Collections.emptyList();
      }

      // Store artifact to set to remove duplicate created by old IDEA (https://youtrack.jetbrains.com/issue/IDEA-72105)
      Collection<MavenArchetype> result = new LinkedHashSet<>();

      List<Element> children = JDOMUtil.load(userArchetypesPath).getChildren(ELEMENT_ARCHETYPE);
      for (int i = children.size() - 1; i >= 0; i--) {
        Element each = children.get(i);

        String groupId = each.getAttributeValue(ELEMENT_GROUP_ID);
        String artifactId = each.getAttributeValue(ELEMENT_ARTIFACT_ID);
        String version = each.getAttributeValue(ELEMENT_VERSION);
        String repository = each.getAttributeValue(ELEMENT_REPOSITORY);
        String description = each.getAttributeValue(ELEMENT_DESCRIPTION);

        if (StringUtil.isEmptyOrSpaces(groupId)
            || StringUtil.isEmptyOrSpaces(artifactId)
            || StringUtil.isEmptyOrSpaces(version)) {
          continue;
        }

        result.add(new MavenArchetype(groupId, artifactId, version, repository, description));
      }

      ArrayList<MavenArchetype> listResult = new ArrayList<>(result);
      Collections.reverse(listResult);

      return listResult;
    }
    catch (IOException | JDOMException e) {
      MavenLog.LOG.warn(e);
      return Collections.emptyList();
    }
  }

  public static void addArchetype(@NotNull MavenArchetype archetype, @NotNull Path userArchetypesPath) {
    List<MavenArchetype> archetypes = new ArrayList<>(loadUserArchetypes(userArchetypesPath));
    int idx = archetypes.indexOf(archetype);
    if (idx >= 0) {
      archetypes.set(idx, archetype);
    }
    else {
      archetypes.add(archetype);
    }

    saveUserArchetypes(archetypes, userArchetypesPath);
  }

  private static void saveUserArchetypes(List<MavenArchetype> userArchetypes,  @NotNull Path userArchetypesPath) {
    Element root = new Element(ELEMENT_ARCHETYPES);
    for (MavenArchetype each : userArchetypes) {
      Element childElement = new Element(ELEMENT_ARCHETYPE);
      childElement.setAttribute(ELEMENT_GROUP_ID, each.groupId);
      childElement.setAttribute(ELEMENT_ARTIFACT_ID, each.artifactId);
      childElement.setAttribute(ELEMENT_VERSION, each.version);
      if (each.repository != null) {
        childElement.setAttribute(ELEMENT_REPOSITORY, each.repository);
      }
      if (each.description != null) {
        childElement.setAttribute(ELEMENT_DESCRIPTION, each.description);
      }
      root.addContent(childElement);
    }
    try {
      JdomKt.write(root, userArchetypesPath);
    }
    catch (IOException e) {
      MavenLog.LOG.warn(e);
    }
  }
}

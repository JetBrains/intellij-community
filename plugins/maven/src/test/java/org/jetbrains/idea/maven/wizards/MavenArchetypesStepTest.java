package org.jetbrains.idea.maven.wizards;

import com.google.common.collect.Lists;
import com.intellij.ide.projectWizard.ProjectWizardTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenArchetype;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;

public class MavenArchetypesStepTest extends ProjectWizardTestCase {

  public void testOrdering() {
    List<MavenArchetype> unsortedList = Lists.newArrayList(
      createArchetype("7.9.2"),
      createArchetype("7.9.1"),
      createArchetype("7.9.0"),
      createArchetype("7.10.0"),
      createArchetype("7.8.2")
    );

    List<MavenArchetype> resultList = groupAndSortArchetypes(unsortedList);

    List<MavenArchetype> sortedList = Lists.newArrayList(
      createArchetype("7.10.0"),
      createArchetype("7.9.2"),
      createArchetype("7.9.1"),
      createArchetype("7.9.0"),
      createArchetype("7.8.2")
    );

    assertEquals(sortedList, resultList);
  }

  public void testNoPatchVersion() {
    List<MavenArchetype> unsortedList = Lists.newArrayList(
      createArchetype("7.9"),
      createArchetype("7.8"),
      createArchetype("7.7"),
      createArchetype("7.10")
    );

    List<MavenArchetype> resultList = groupAndSortArchetypes(unsortedList);

    List<MavenArchetype> sortedList = Lists.newArrayList(
      createArchetype("7.10"),
      createArchetype("7.9"),
      createArchetype("7.8"),
      createArchetype("7.7")
    );

    assertEquals(sortedList, resultList);
  }

  public void testWithClassifiers() {
    List<MavenArchetype> unsortedList = Lists.newArrayList(
      createArchetype("7.9.0-m02"),
      createArchetype("7.9.0-m01"),
      createArchetype("7.9.0-RELEASE"),
      createArchetype("7.10.0")
    );

    List<MavenArchetype> resultList = groupAndSortArchetypes(unsortedList);

    List<MavenArchetype> sortedList = Lists.newArrayList(
      createArchetype("7.10.0"),
      createArchetype("7.9.0-RELEASE"),
      createArchetype("7.9.0-m02"),
      createArchetype("7.9.0-m01")
    );

    assertEquals(sortedList, resultList);
  }

  @NotNull
  private static List<MavenArchetype> groupAndSortArchetypes(List<MavenArchetype> unsortedList) {
    TreeNode node = MavenArchetypesStep.groupAndSortArchetypes(new HashSet<>(unsortedList));
    Enumeration o = ((DefaultMutableTreeNode)node.children().nextElement()).children();

    List<MavenArchetype> mavenArchetypes = new ArrayList<>();

    while (o.hasMoreElements()) {
      mavenArchetypes.add((MavenArchetype) ((DefaultMutableTreeNode)o.nextElement()).getUserObject());
    }

    return mavenArchetypes;
  }

  @NotNull
  private static MavenArchetype createArchetype(String version) {
    return new MavenArchetype(
      "com.example",
      "an-archetype",
      version,
      null,
      null
    );
  }
}

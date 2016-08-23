package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.util.Comparing;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.*;

/**
 * @author Sergey Evdokimov
 */
public class MavenProjectNamer {

  //private static Logger LOG = Logger.getInstance(MavenProjectNamer.class);

  public static Map<MavenProject, String> generateNameMap(Collection<MavenProject> mavenProjects) {
    MultiMap<String, MavenProject> artifactIdMap = new MultiMap<>();

    for (MavenProject project : mavenProjects) {
      artifactIdMap.putValue(project.getMavenId().getArtifactId(), project);
    }

    Map<MavenProject, String> res = new THashMap<>();

    for (Map.Entry<String, Collection<MavenProject>> entry : artifactIdMap.entrySet()) {
      List<MavenProject> projectList = (List<MavenProject>)entry.getValue();
      String artifactId = entry.getKey();

      if (projectList.size() == 1) {
        res.put(projectList.get(0), artifactId);
      }
      else if (allGroupsAreDifferent(projectList)) {
        for (MavenProject mavenProject : projectList) {
          res.put(mavenProject, mavenProject.getMavenId().getGroupId() + ':' + mavenProject.getMavenId().getArtifactId());
        }
      } else if (allGroupsEqual(mavenProjects)) {
        for (MavenProject mavenProject : projectList) {
          res.put(mavenProject, mavenProject.getMavenId().getArtifactId() + ':' + mavenProject.getMavenId().getVersion());
        }
      }
      else {
        for (MavenProject mavenProject : projectList) {
          MavenId mavenId = mavenProject.getMavenId();
          res.put(mavenProject, mavenId.getGroupId() + ':' + mavenId.getArtifactId() + ':' + mavenId.getVersion());
        }
      }
    }

    return res;
  }

  private static boolean allGroupsEqual(Collection<MavenProject> mavenProjects) {
    Iterator<MavenProject> itr = mavenProjects.iterator();

    if (!itr.hasNext()) return true;

    String groupId = itr.next().getMavenId().getGroupId();

    while (itr.hasNext()) {
      MavenProject mavenProject = itr.next();

      if (!Comparing.equal(groupId, mavenProject.getMavenId().getGroupId())) {
        return false;
      }
    }

    return true;
  }

  private static boolean allGroupsAreDifferent(Collection<MavenProject> mavenProjects) {
    Set<String> exitingGroups = new THashSet<>();

    for (MavenProject mavenProject : mavenProjects) {
      if (!exitingGroups.add(mavenProject.getMavenId().getGroupId())) {
        return false;
      }
    }

    return true;
  }

  private static void doBuildProjectTree(MavenProjectsManager manager, Map<MavenProject, Integer> res, List<MavenProject> rootProjects, int depth) {
    MavenProject[] rootProjectArray = rootProjects.toArray(new MavenProject[rootProjects.size()]);
    Arrays.sort(rootProjectArray, new MavenProjectComparator());

    for (MavenProject project : rootProjectArray) {
      if (!res.containsKey(project)) {
        res.put(project, depth);

        doBuildProjectTree(manager, res, manager.getModules(project), depth + 1);
      }
    }
  }

  public static Map<MavenProject, Integer> buildProjectTree(MavenProjectsManager manager) {
    Map<MavenProject, Integer> res = new LinkedHashMap<>();

    doBuildProjectTree(manager, res, manager.getRootProjects(), 0);

    return res;
  }

  public static class MavenProjectComparator implements Comparator<MavenProject> {
    @Override
    public int compare(MavenProject o1, MavenProject o2) {
      MavenId id1 = o1.getMavenId();
      MavenId id2 = o2.getMavenId();

      int res = Comparing.compare(id1.getGroupId(), id2.getGroupId());
      if (res != 0) return res;

      res = Comparing.compare(id1.getArtifactId(), id2.getArtifactId());
      if (res != 0) return res;

      res = Comparing.compare(id1.getVersion(), id2.getVersion());
      return res;
    }
  }
}

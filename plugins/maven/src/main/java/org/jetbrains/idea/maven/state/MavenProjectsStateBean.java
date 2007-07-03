/*
 * Created by IntelliJ IDEA.
 * User: Vladislav.Kaznacheev
 * Date: Jun 29, 2007
 * Time: 1:02:29 PM
 */
package org.jetbrains.idea.maven.state;

import java.util.*;

public class MavenProjectsStateBean {
  public List<String> ignoredPathMasks = new ArrayList<String>();
  public Set<String> ignoredFiles = new TreeSet<String>();
  public Map<String, Collection<String>> activeProfiles = new HashMap<String, Collection<String>>();
}
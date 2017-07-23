/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.updater;

import java.io.File;
import java.util.*;

public class DiffCalculator {
  public static Result calculate(Map<String, Long> oldChecksums, Map<String, Long> newChecksums) {
    return calculate(oldChecksums, newChecksums, Collections.emptyList(), Collections.emptyList(), false);
  }

  public static Result calculate(Map<String, Long> oldChecksums,
                                 Map<String, Long> newChecksums,
                                 List<String> critical,
                                 List<String> optional,
                                 boolean lookForMoved) {
    Result result = new Result();
    result.commonFiles = collect(oldChecksums, newChecksums, critical, true);
    result.filesToDelete = withAllRemoved(oldChecksums, newChecksums);

    Map<String, Long> toUpdate = collect(oldChecksums, newChecksums, critical, false);
    Map<String, Long> toCreate = withAllRemoved(newChecksums, oldChecksums);

    result.filesToCreate = lookForMoved ? new LinkedHashMap<>() : toCreate;

    result.filesToUpdate = new LinkedHashMap<>();
    for (Map.Entry<String, Long> update : toUpdate.entrySet()) {
      if (Digester.isSymlink(update.getValue())) {
        result.filesToDelete.put(update.getKey(), update.getValue());
        result.filesToCreate.put(update.getKey(), Digester.INVALID);
      }
      else {
        result.filesToUpdate.put(update.getKey(), new Update(update.getKey(), update.getValue(), false));
      }
    }

    if (lookForMoved) {
      Map<Long, List<String>> byContent = groupFilesByContent(result.filesToDelete);
      Map<String, List<String>> byName = groupFilesByName(result.filesToDelete);

      for (Map.Entry<String, Long> create : toCreate.entrySet()) {
        if (Digester.isFile(create.getValue())) {
          List<String> sameContent = byContent.get(create.getValue());
          String source = findBestCandidateForMove(sameContent, create.getKey(), optional);
          boolean move = true;

          if (source == null) {
            List<String> sameName = byName.get(new File(create.getKey()).getName());
            source = findBestCandidateForMove(sameName, create.getKey(), optional);
            move = false;
          }

          if (source != null && !critical.contains(source)) {
            result.filesToUpdate.put(create.getKey(), new Update(source, result.filesToDelete.get(source), move));
            continue;
          }
        }

        result.filesToCreate.put(create.getKey(), create.getValue());
      }
    }

    return result;
  }

  private static int compareRootFolders(String[] dirs, String[] others) {
    int matches = 0;
    for (int i = 0; i < dirs.length && i < others.length; i++) {
      if (dirs[i].equals(others[i])) {
        matches = i + 1;
      }
      else {
        break;
      }
    }
    return matches;
  }

  private static String findBestCandidateForMove(List<String> paths, String path, List<String> optional) {
    if (paths == null) return null;

    boolean mandatory = !optional.contains(path);
    String best = "";

    String[] dirs = path.split("/");
    int common = 0;
    for (String other : paths) {
      if (mandatory && optional.contains(other)) continue;  // mandatory targets must not use optional sources
      String[] others = other.split("/");
      for (int i = 0; i < dirs.length && i < others.length; i++) {
        if (dirs[dirs.length - i - 1].equals(others[others.length - i - 1])) {
          if (i + 1 > common) {
            best = other;
            common = i + 1;
          }
          // check root folders of candidates with the same matches
          else if (i + 1 == common && compareRootFolders(dirs, best.split("/")) < compareRootFolders(dirs, other.split("/"))) {
            best = other;
          }
        }
        else {
          break;
        }
      }
    }

    return !best.isEmpty() ? best : null;
  }

  public static Map<Long, List<String>> groupFilesByContent(Map<String, Long> map) {
    Map<Long, List<String>> result = new HashMap<>();
    for (Map.Entry<String, Long> entry : map.entrySet()) {
      String path = entry.getKey();
      if (!path.endsWith("/")) {
        Long hash = entry.getValue();
        List<String> paths = result.get(hash);
        if (paths == null) result.put(hash, (paths = new LinkedList<>()));
        paths.add(path);
      }
    }
    return result;
  }

  private static Map<String, List<String>> groupFilesByName(Map<String, Long> toDelete) {
    Map<String, List<String>> result = new HashMap<>();
    for (String path : toDelete.keySet()) {
      if (!path.endsWith("/")) {
        String name = new File(path).getName();
        List<String> paths = result.get(name);
        if (paths == null) result.put(name, (paths = new LinkedList<>()));
        paths.add(path);
      }
    }
    return result;
  }

  private static Map<String, Long> withAllRemoved(Map<String, Long> from, Map<String, Long> toRemove) {
    Map<String, Long> result = new LinkedHashMap<>(from);
    for (String each : toRemove.keySet()) {
      result.remove(each);
    }
    return result;
  }

  private static Map<String, Long> collect(Map<String, Long> older, Map<String, Long> newer, List<String> critical, boolean equal) {
    Map<String, Long> result = new LinkedHashMap<>();
    for (Map.Entry<String, Long> each : newer.entrySet()) {
      String file = each.getKey();
      Long oldChecksum = older.get(file);
      Long newChecksum = newer.get(file);
      if (oldChecksum != null && newChecksum != null && (oldChecksum.equals(newChecksum) && !critical.contains(file)) == equal) {
        result.put(file, oldChecksum);
      }
    }
    return result;
  }

  public static class Update {
    public final String source;
    public final long checksum;
    public final boolean move;

    public Update(String source, long checksum, boolean move) {
      this.checksum = checksum;
      this.source = source;
      this.move = move;
    }
  }

  public static class Result {
    public Map<String, Long> filesToDelete;
    public Map<String, Long> filesToCreate;
    public Map<String, Update> filesToUpdate;
    public Map<String, Long> commonFiles;
  }
}
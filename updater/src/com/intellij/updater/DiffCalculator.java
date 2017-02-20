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
  public static Result calculate(Map<String, Long> oldChecksums,
                                 Map<String, Long> newChecksums,
                                 List<String> critical,
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
      Map<Long, String> byContent = inverse(result.filesToDelete);
      Map<String, List<String>> byName = groupFilesByName(result.filesToDelete);

      for (Map.Entry<String, Long> create : toCreate.entrySet()) {
        if (Digester.isFile(create.getValue())) {
          String source = byContent.get(create.getValue());
          boolean move = true;

          if (source == null) {
            List<String> sameName = byName.get(new File(create.getKey()).getName());
            if (sameName != null) {
              source = findBestCandidateForMove(sameName, create.getKey());
              move = false;
            }
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

  private static String findBestCandidateForMove(List<String> paths, String path) {
    int common = 0;
    String best = "";
    String[] dirs = path.split("/");
    for (String other : paths) {
      String[] others = other.split("/");
      for (int i = 0; i < dirs.length && i < others.length; i++) {
        if (dirs[dirs.length - i - 1].equals(others[others.length - i - 1])) {
          if (i + 1 > common) {
            best = other;
            common = i + 1;
          }
        } else {
          break;
        }
      }
    }
    return best;
  }

  private static Map<String, List<String>> groupFilesByName(Map<String, Long> toDelete) {
    Map<String, List<String>> result = new HashMap<>();
    for (String path : toDelete.keySet()) {
      if (!path.endsWith("/")) {
        String name = new File(path).getName();
        List<String> paths = result.get(name);
        if (paths == null) {
          paths = new LinkedList<>();
          result.put(name, paths);
        }
        paths.add(path);
      }
    }
    return result;
  }

  public static Map<Long,String> inverse(Map<String, Long> map) {
    Map<Long, String> inv = new LinkedHashMap<>();
    for (Map.Entry<String, Long> entry : map.entrySet()) {
      inv.put(entry.getValue(), entry.getKey());
    }
    return inv;
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
package com.intellij.updater;

import java.io.File;
import java.util.*;

public class DiffCalculator {
  public static Result calculate(Map<String, Long> oldChecksums, Map<String, Long> newChecksums, List<String> critical, boolean move) {
    Result result = new Result();
    result.commonFiles = collect(oldChecksums, newChecksums, critical, true);
    result.filesToDelete = withAllRemoved(oldChecksums, newChecksums);

    Map<String, Long> toUpdate = collect(oldChecksums, newChecksums, critical, false);
    Map<String, Long> toCreate = withAllRemoved(newChecksums, oldChecksums);

    // Some creates become updates if found in different directories.
    result.filesToCreate = new LinkedHashMap<>();
    result.filesToUpdate = new LinkedHashMap<>();

    for (Map.Entry<String, Long> update : toUpdate.entrySet()) {
      result.filesToUpdate.put(update.getKey(), new Update(update.getKey(), update.getValue(), false));
    }

    if (move) {
      Map<Long, String> byContent = inverse(result.filesToDelete);
      Map<String, List<String>> byName = groupFilesByName(result.filesToDelete);

      // Find first by content
      for (Map.Entry<String, Long> create : toCreate.entrySet()) {
        boolean isDir = create.getKey().endsWith("/");
        String source = byContent.get(create.getValue());
        boolean found = false;
        if (source != null && !isDir) {
          // Found a file with the same content use it, unless it's critical
          if (!critical.contains(source)) {
            result.filesToUpdate.put(create.getKey(), new Update(source, result.filesToDelete.get(source), true));
            found = true;
          }
        }
        else {
          File fileToCreate = new File(create.getKey());
          List<String> sameName = byName.get(fileToCreate.getName());
          if (sameName != null && !isDir) {
            String best = findBestCandidateForMove(sameName, create.getKey());
            // Found a file with the same name, if it's not critical use it, worst case as big as a create.
            if (!critical.contains(best)) {
              result.filesToUpdate.put(create.getKey(), new Update(best, result.filesToDelete.get(best), false));
              found = true;
            }
          }
        }
        if (!found) {
          // Fine, just create it.
          result.filesToCreate.put(create.getKey(), create.getValue());
        }
      }
    } else {
      result.filesToCreate = toCreate;
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
      if (oldChecksum != null && newChecksum != null) {
        if ((oldChecksum.equals(newChecksum) && !critical.contains(file)) == equal) {
          result.put(file, oldChecksum);
        }
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

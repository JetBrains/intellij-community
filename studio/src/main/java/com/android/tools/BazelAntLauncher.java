/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools;

import java.io.*;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Arrays;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import org.apache.tools.ant.launch.Launcher;

class BazelAntLauncher {

  public static void main(String[] args) throws Exception {

    String win = null;
    String mac = null;
    String linux = null;
    String binDir = null;
    String genDir = null;
    String build = null;
    String tmp = null;
    String out = null;
    String moduleInfo = null;
    String artifactInfo = null;

    Iterator<String> it = Arrays.asList(args).iterator();
    while (it.hasNext()) {
        String arg = it.next();
        if (arg.equals("--win")) {
            win = it.next();
        } else if (arg.equals("--mac") && it.hasNext()) {
            mac = it.next();
        } else if (arg.equals("--linux") && it.hasNext()) {
            linux = it.next();
        } else if (arg.equals("--bin_dir") && it.hasNext()) {
            binDir = it.next();
        } else if (arg.equals("--gen_dir") && it.hasNext()) {
            genDir = it.next();
        } else if (arg.equals("--build") && it.hasNext()) {
            build = it.next();
        } else if (arg.equals("--tmp") && it.hasNext()) {
            tmp = it.next();
        } else if (arg.equals("--module_info") && it.hasNext()) {
            moduleInfo = it.next();
        } else if (arg.equals("--artifact_info") && it.hasNext()) {
          artifactInfo = it.next();
        } else if (arg.equals("--out") && it.hasNext()) {
            out = it.next();
        } else {
            System.err.println("Unknown argument: " + arg);
            return;
        }
    }
    if (tmp == null) {
        System.err.println("Working directory unspecified. Use --tmp.");
        return;
    }
    if (binDir == null) {
        System.err.println("Directory to bind to bazel-bin not specified. Use --bin_dir.");
        return;
    }
    if (genDir == null) {
        System.err.println("Directory to bind to bazel-genfiles not specified. Use --gen_dir.");
        return;
    }
    if (build == null) {
        System.err.println("Build file not specified. Use --build.");
        return;
    }
    Path tmpPath = Paths.get(tmp);
    Files.createDirectory(tmpPath);

    Path bazelBin = Paths.get("bazel-bin");
    Path bazelGenfiles = Paths.get("bazel-genfiles");

    Files.createSymbolicLink(bazelBin, Paths.get(binDir));
    Files.createSymbolicLink(bazelGenfiles, Paths.get(genDir));

    // TODO: Once we package gradle from bazel, we don't need to fake an empty directory
    Files.createDirectories(Paths.get("out/studio/repo"));

    File modules = makeAbsolute(moduleInfo);
    File artifacts = makeAbsolute(artifactInfo);

    // Invoke ant in a separate process as it uses System.exit
    File output = new File(out);
    ProcessBuilder process = new ProcessBuilder(
            System.getProperty("java.home") + "/bin/java",
            "-classpath",
            System.getProperty("java.class.path"),
            Launcher.class.getCanonicalName(),
            "-f",
            build,
            "build",
            "-Dintellij.build.output.root=" + tmpPath.toAbsolutePath(),
            "-Dintellij.build.compiled.modules=" + modules.getAbsolutePath(),
            "-Dintellij.build.compiled.artifacts=" + artifacts.getAbsolutePath(),
            "-Dbundle.kotlin.plugin=true",
            "-Dskip.bazel.dependencies=true"
        ).inheritIO().redirectOutput(output);
    process.environment().put("ANDROID_SDK_HOME", tmpPath.toAbsolutePath().toString());
    int status = process.start().waitFor();
    System.out.println("Ant process ended with status: " + status);
    if (status == 0) {
        String buildNumber = Files.readAllLines(Paths.get("tools/idea/build.txt"), Charset.defaultCharset()).get(0);
        String filePrefix = "android-studio-" + buildNumber;
        if (win != null) {
            Files.move(Paths.get(tmp, "artifacts", filePrefix + ".win.zip"), Paths.get(win));
        }
        if (mac != null) {
            Files.move(Paths.get(tmp, "artifacts", filePrefix + ".mac.zip"), Paths.get(mac));
        }
        if (linux != null) {
            Files.move(Paths.get(tmp, "artifacts", filePrefix + ".tar.gz"), Paths.get(linux));
        }
    } else {
        try (Stream<String> stream = Files.lines(output.toPath())) {
            // TODO: An artifact of multiple modules sharing the same .jar is that ant complains
            // when it sees duplicated entried.
            stream.filter(line -> !line.endsWith("already added, skipping")).forEach(System.out::println);
        }
    }

    Files.walk(Paths.get(tmp))
                .map(Path::toFile)
                .sorted((o1, o2) -> -o1.compareTo(o2))
                .forEach(File::delete);
    System.exit(status);
  }

  private static File makeAbsolute(String propertiesPath) throws IOException {
    File file = new File(propertiesPath);
    try (FileInputStream input = new FileInputStream(file)) {
      Properties properties = new Properties();
      properties.load(input);
      for (String name : properties.stringPropertyNames()) {
        String[] values = properties.getProperty(name).split(":");
        for (int i = 0; i < values.length; i++) {
          values[i] = new File(values[i]).getAbsolutePath();
        }
        properties.put(name, String.join(":", values));
      }
      File newFile = File.createTempFile(file.getName(), null);
      properties.store(new FileOutputStream(newFile), "");
      return newFile;
    }
  }
}

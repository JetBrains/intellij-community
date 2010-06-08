package org.jetbrains.idea.maven.facade;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.CharBuffer;
import java.rmi.registry.LocateRegistry;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public class MavenFacadeLocator {

  private static final String PORT_ID_PREFIX = "Port/ID:";

  public static Pair<MavenFacade, Process> acquireFacade(String[] cmdArray, String[] envP, File dir) throws Exception {
    final Process process = Runtime.getRuntime().exec(cmdArray, envP, dir);
    final Ref<Pair<Integer, String>> result = Ref.create(null);
    final Runnable target = new Runnable() {
      public void run() {
        try {
          eatOutStreams(process, result);
        }
        catch (Exception e) {
          if (result.isNull()) {
            synchronized (result) {
              result.notifyAll();
            }
          }
        }
      }
    };
    final Thread thread = new Thread(target, "Process Streams Reader");
    thread.setDaemon(true);
    thread.start();
    synchronized (result) {
      if (result.isNull()) {
        result.wait();
      }
    }
    if (result.isNull()) {
      throw new RuntimeException();
    }
    else {
      return Pair.create((MavenFacade)LocateRegistry.getRegistry(result.get().first).lookup(result.get().second), process);
    }
  }

  private static void eatOutStreams(Process process, Ref<Pair<Integer, String>> result) throws Exception {
    final InputStream stderr = process.getErrorStream();
    final InputStream stdout = process.getInputStream();
    final Reader errReader = new InputStreamReader(stderr);
    final Reader outReader = new InputStreamReader(stdout);
    final StringBuilder err = new StringBuilder();
    final StringBuilder out = new StringBuilder();
    final CharBuffer buffer = CharBuffer.allocate(1024);
    int curOutIndex = 0;

    while (true) {
      while (stderr.available() > 0) {
        final int count = errReader.read(buffer);
        err.append(buffer.array(), 0, count);
      }
      while (stdout.available() > 0) {
        final int count = outReader.read(buffer);
        out.append(buffer.array(), 0, count);
      }
      if (err.length() > 0) {
        if (result.isNull()) {
          break;
        }
        System.out.println(err);
        err.setLength(0);
      }
      if (out.length() > 0) {
        int nlIndex;
        while ((nlIndex = out.indexOf("\n", curOutIndex)) >= 0) {
          final String text = out.substring(curOutIndex, nlIndex);
          curOutIndex = nlIndex + 1;
          if (text.startsWith(PORT_ID_PREFIX)) {
            final String pair = text.substring(PORT_ID_PREFIX.length()).trim();
            final int idx = pair.indexOf("/");
            final int port = Integer.parseInt(pair.substring(0, idx));
            final String name = pair.substring(idx + 1);
            System.out.println("Connecting to: localhost:" + port + ". Looking up: " + name);
            synchronized (result) {
              result.set(Pair.create(port, name));
              result.notifyAll();
            }
          }
        }
        curOutIndex = 0;
        out.setLength(0);
      }
      Thread.sleep(1000L);
    }
    System.out.println("Destroying process");
    process.destroy();
    if (result.isNull()) {
      synchronized (result) {
        result.notifyAll();
      }
    }
  }

  public static void main(String[] args) throws Exception {
    final String systemPath;
    final ClassLoader loader = MavenFacadeLocator.class.getClassLoader();
    if (loader instanceof URLClassLoader) {
      final URL[] urls = ((URLClassLoader)loader).getURLs();
      final StringBuilder sb = new StringBuilder();
      for (URL url : urls) {
        if (sb.length() > 0) sb.append(File.pathSeparator);
        sb.append(url.getFile().replace('/', File.separatorChar));
      }
      systemPath = sb.toString();
    }
    else {
      systemPath = System.getProperty("java.class.path");
    }

    final String debug1 = "-Xdebug";
    final String debug2 = "-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5009";
    final Pair<MavenFacade, Process> pair =
      acquireFacade(new String[]{"java", "-cp", systemPath, debug1, debug2, RemoteMavenServer.class.getName()}, null, null);
    final MavenFacade facade = pair.first;
    final String lookup = args.length > 0 ? args[0] : "hibernate";
    System.out.println("Looking up: " + lookup);
    final List<MavenArtifactInfo> result = facade.findArtifacts(createTemplate(lookup), "http://repository.sonatype.org/service/local/");
    int i = 1;
    for (MavenArtifactInfo type : result) {
      System.out.println((++i) + ". " + type.getGroupId() + ":" + type.getArtifactId() + ":" + type.getVersion());
    }
    pair.second.destroy();
    System.exit(0);
  }

  private static MavenArtifactInfo createTemplate(String coord) {
    final String[] parts = coord.split(":");
    return new MavenArtifactInfo(parts.length > 0 ? parts[0] : null,
                                 parts.length > 1 ? parts[1] : null,
                                 parts.length > 2 ? parts[2] : null,
                                 null, null, null, null);
  }

}

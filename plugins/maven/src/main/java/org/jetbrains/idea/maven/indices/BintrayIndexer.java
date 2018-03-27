// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices;

import com.google.gson.*;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.server.IndexedMavenId;
import org.jetbrains.idea.maven.server.MavenIndicesProcessor;
import org.jetbrains.idea.maven.server.MavenServerIndexerException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.openapi.util.text.StringUtil.split;

/**
 * @author ibessonov
 */
class BintrayIndexer implements NotNexusIndexer {

  private final String myUrlTemplate;

  public BintrayIndexer(@NotNull String subject, @Nullable String repo) {
    myUrlTemplate = "https://bintray.com/api/v1/search/packages/maven?q=*&subject=" + subject
                    + (repo != null ? "&repo=" + repo : "");
  }

  @Override
  public void processArtifacts(MavenProgressIndicator progress, MavenIndicesProcessor processor)
      throws IOException, MavenServerIndexerException {
    AtomicReference<Exception> exception = new AtomicReference<>();

    HttpRequests.request(myUrlTemplate).accept("application/json").connect(request -> {
      URLConnection urlConnection = request.getConnection();

      int total = urlConnection.getHeaderFieldInt("X-RangeLimit-Total", -1);
      if (total > 0) {
        fetchMavenIds(request, processor);

        int endPos = urlConnection.getHeaderFieldInt("X-RangeLimit-EndPos", Integer.MAX_VALUE);
        if (endPos < total) {
          progress.pushState();
          progress.setIndeterminate(false);

          try {
            int totalIterations = (total - 1) / endPos;
            int threadsCount = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

            boolean useThreadPool = threadsCount > 1 && totalIterations >= 10;
            CountDownLatch cdl = useThreadPool ? new CountDownLatch(threadsCount) : null;

            AtomicInteger iterationsCounter = new AtomicInteger(0);
            Runnable task = () -> {
              try {
                while (true) {
                  int i = iterationsCounter.incrementAndGet();
                  if (i > totalIterations || progress.isCanceled()) {
                    break;
                  }

                  try {
                    HttpRequests.request(myUrlTemplate + "&start_pos=" + (i * endPos)).accept("application/json").connect(r -> {
                      fetchMavenIds(r, processor);
                      progress.setFraction(1d * iterationsCounter.get() / totalIterations);
                      return null;
                    });
                  }
                  catch (Exception e) {
                    exception.set(e);
                    break;
                  }
                }
              }
              finally {
                if (useThreadPool) {
                  cdl.countDown();
                }
              }
            };
            if (useThreadPool) {
              ExecutorService executorService = Executors.newFixedThreadPool(threadsCount);
              for (int i = 0; i < threadsCount; i++) {
                executorService.submit(task);
              }
              try {
                cdl.await();
              }
              catch (InterruptedException ignored) {
              }
            }
            else {
              for (int i = 0; i < totalIterations; i++) {
                task.run();
              }
            }
          }
          finally {
            progress.popState();
          }
        }
      }
      return null;
    });

    Exception e = exception.get();
    if (e != null) {
      if (e instanceof IOException) {
        throw (IOException)e;
      }
      if (e instanceof MavenServerIndexerException) {
        throw (MavenServerIndexerException)e;
      }
      throw new MavenServerIndexerException(e);
    }
  }

  protected void fetchMavenIds(HttpRequests.Request request, MavenIndicesProcessor processor) throws IOException {
    try (InputStream in = request.getInputStream()) {
      JsonElement element = new JsonParser().parse(new InputStreamReader(in));
      JsonArray array = element.getAsJsonArray();
      if (array == null) {
        throw new IOException("Unexpected response format, JSON array expected from " + request.getURL());
      }

      List<IndexedMavenId> mavenIds = new ArrayList<>();
      for (JsonElement el : array) {
        JsonObject jo = el.getAsJsonObject();
        JsonArray systemIds = jo.getAsJsonArray("system_ids");
        JsonArray versions = jo.getAsJsonArray("versions");
        JsonElement desc = jo.get("desc");
        String description = desc == null || desc == JsonNull.INSTANCE ? null : desc.getAsString();

        if (systemIds != null && versions != null) {
          for (JsonElement systemId : systemIds) {
            String groupAndArtifactId = systemId.getAsString();
            List<String> list = split(groupAndArtifactId, ":");
            if (list.size() != 2) continue;

            String groupId = list.get(0);
            String artifactId = list.get(1);
            for (JsonElement version : versions) {
              mavenIds.add(new IndexedMavenId(groupId, artifactId, version.getAsString(), null, description));
            }
          }
        }
      }
      synchronized (this) {
        processor.processArtifacts(mavenIds);
      }
    }
  }
}

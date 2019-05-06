// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.onlinecompletion.central;


import com.google.gson.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItemWithClass;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MavenCentralModel {

  public ResponseHeader responseHeader;
  public Response response;

  public Highlighting highlighting;


  public static class ResponseHeader {
    public int status;
  }

  public static class Response {
    public int numFound;
    public int start;
    public FoundDoc[] docs;
    public Highlighting highlighting;

    public static class FoundDoc {
      public String id;
      public String g; //group
      public String a; //artifactId
      public String v; //version
      public String latestVersion;
      public String repositoryId;
      public String p; //packaging
      public int versionCount;
      public long timestamp;
    }
  }

  public static class Highlighting {
    public List<MavenDependencyCompletionItemWithClass> results;
  }

  public static class HighlightingDeserializer implements JsonDeserializer<Highlighting> {

    @Override
    public Highlighting deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
      if (json == null) {
        return null;
      }
      JsonObject object = json.getAsJsonObject();
      Highlighting result = new Highlighting();
      result.results = new ArrayList<>(object.size());

      for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
        JsonArray fch = entry.getValue().getAsJsonObject().get("fch").getAsJsonArray();
        String[] hightlights = new String[fch.size()];

        for (int i = 0; i < hightlights.length; i++) {
          hightlights[i] = clearEM(fch.get(i).getAsString());
        }
        result.results.add(new MavenDependencyCompletionItemWithClass(entry.getKey(), MavenDependencyCompletionItem.Type.REMOTE,
                                                                      ContainerUtil.newArrayList(hightlights)));
      }
      return result;
    }

    private static String clearEM(String string) {
      return string.replaceAll("<em>", "").replaceAll("</em>", "");
    }
  }
}

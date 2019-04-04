/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.tasks.trello;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.intellij.tasks.impl.gson.TaskGsonUtil;
import com.intellij.tasks.trello.model.TrelloBoard;
import com.intellij.tasks.trello.model.TrelloCard;
import com.intellij.tasks.trello.model.TrelloCommentAction;
import com.intellij.tasks.trello.model.TrelloList;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static com.intellij.tasks.trello.model.TrelloLabel.LabelColor;

/**
 * @author Mikhail Golubev
 */
public class TrelloUtil {
  public static final Pattern TRELLO_ID_PATTERN = Pattern.compile("[a-z0-9]{24}");
  public static final Gson GSON = buildGson();
  public static final String TRELLO_API_BASE_URL = "https://api.trello.com/1";
  public static final TypeToken<List<TrelloCard>> LIST_OF_CARDS_TYPE = new TypeToken<List<TrelloCard>>() { /* empty */ };
  public static final TypeToken<List<TrelloBoard>> LIST_OF_BOARDS_TYPE = new TypeToken<List<TrelloBoard>>() { /* empty */ };
  public static final TypeToken<List<TrelloList>> LIST_OF_LISTS_TYPE = new TypeToken<List<TrelloList>>() { /* empty */ };
  public static final TypeToken<List<TrelloCommentAction>> LIST_OF_COMMENTS_TYPE = new TypeToken<List<TrelloCommentAction>>() { /* empty */ };

  private static Gson buildGson() {
    final GsonBuilder gson = TaskGsonUtil.createDefaultBuilder();
    gson.registerTypeAdapter(LabelColor.class, new LabelColorDeserializer());
    return gson.create();
  }

  private static class LabelColorDeserializer implements JsonDeserializer<LabelColor> {
    @Override
    public LabelColor deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
      final String colorName = json.getAsString().toUpperCase(Locale.US);
      if (colorName.isEmpty()) {
        return LabelColor.NO_COLOR;
      }
      try {
        return LabelColor.valueOf(colorName);
      }
      catch (IllegalArgumentException e) {
        return LabelColor.NO_COLOR;
      }
    }
  }
}

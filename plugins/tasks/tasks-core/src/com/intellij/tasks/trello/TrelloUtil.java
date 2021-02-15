// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.tasks.trello;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.impl.gson.TaskGsonUtil;
import com.intellij.tasks.trello.model.TrelloBoard;
import com.intellij.tasks.trello.model.TrelloCard;
import com.intellij.tasks.trello.model.TrelloCommentAction;
import com.intellij.tasks.trello.model.TrelloList;

import java.lang.reflect.Type;
import java.util.List;
import java.util.regex.Pattern;

import static com.intellij.tasks.trello.model.TrelloLabel.LabelColor;

/**
 * @author Mikhail Golubev
 */
public final class TrelloUtil {
  public static final Pattern TRELLO_ID_PATTERN = Pattern.compile("[a-z0-9]{24}");
  public static final Gson GSON = buildGson();
  public static final String TRELLO_API_BASE_URL = "https://api.trello.com/1";
  public static final TypeToken<List<TrelloCard>> LIST_OF_CARDS_TYPE = new TypeToken<>() { /* empty */
  };
  public static final TypeToken<List<TrelloBoard>> LIST_OF_BOARDS_TYPE = new TypeToken<>() { /* empty */
  };
  public static final TypeToken<List<TrelloList>> LIST_OF_LISTS_TYPE = new TypeToken<>() { /* empty */
  };
  public static final TypeToken<List<TrelloCommentAction>> LIST_OF_COMMENTS_TYPE = new TypeToken<>() { /* empty */
  };

  private static Gson buildGson() {
    final GsonBuilder gson = TaskGsonUtil.createDefaultBuilder();
    gson.registerTypeAdapter(LabelColor.class, new LabelColorDeserializer());
    return gson.create();
  }

  private static class LabelColorDeserializer implements JsonDeserializer<LabelColor> {
    @Override
    public LabelColor deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
      final String colorName = StringUtil.toUpperCase(json.getAsString());
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

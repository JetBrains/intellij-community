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

import com.google.gson.JsonParseException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepositoryType;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import com.intellij.tasks.trello.model.TrelloBoard;
import com.intellij.tasks.trello.model.TrelloCard;
import com.intellij.tasks.trello.model.TrelloList;
import com.intellij.tasks.trello.model.TrelloUser;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.util.EncodingUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

/**
 * @author Mikhail Golubev
 */
@Tag("Trello")
public final class TrelloRepository extends BaseRepositoryImpl {

  private static final Logger LOG = Logger.getInstance("#com.intellij.tasks.trello.TrelloRepository");

  // User is actually needed only to check ownership of card (by its id)
  private TrelloUser myCurrentUser;
  private TrelloBoard myCurrentBoard;
  private TrelloList myCurrentList;

  /**
   * Serialization constructor
   */
  @SuppressWarnings("UnusedDeclaration")
  public TrelloRepository() {
  }

  /**
   * Normal instantiation constructor
   */
  public TrelloRepository(TaskRepositoryType type) {
    super(type);
  }

  /**
   * Cloning constructor
   */
  public TrelloRepository(TrelloRepository other) {
    super(other);
    myCurrentUser = other.myCurrentUser;
    myCurrentBoard = other.myCurrentBoard;
    myCurrentList = other.myCurrentList;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;
    if (o.getClass() != getClass()) return false;
    TrelloRepository repository = (TrelloRepository)o;
    if (!Comparing.equal(myCurrentUser, repository.myCurrentUser)) return false;
    if (!Comparing.equal(myCurrentBoard, repository.myCurrentBoard)) return false;
    if (!Comparing.equal(myCurrentList, repository.myCurrentList)) return false;
    return true;
  }

  @Override
  public BaseRepository clone() {
    return new TrelloRepository(this);
  }

  @Override
  public Task[] getIssues(@Nullable String query, int max, long since) throws Exception {
    List<TrelloCard> cards = fetchCards();
    return ContainerUtil.map2Array(cards, Task.class, new Function<TrelloCard, Task>() {
      @Override
      public Task fun(TrelloCard card) {
        return new TrelloTask(card, TrelloRepository.this);
      }
    });
  }

  @Nullable
  @Override
  public Task findTask(String id) throws Exception {
    String url = TrelloUtil.TRELLO_API_BASE_URL + "/cards/" + id + "?actions=commentCard";
    try {
      return new TrelloTask(makeRequestAndDeserializeJsonResponse(url, TrelloCard.class), this);
    }
    // Trello returns string "The requested resource was not found." or "invalid id"
    // if card can't be found
    catch (JsonParseException e) {
      return null;
    }
  }

  @Nullable
  public TrelloUser getCurrentUser() {
    return myCurrentUser;
  }

  public void setCurrentUser(TrelloUser currentUser) {
    myCurrentUser = currentUser;
  }

  @Nullable
  public TrelloBoard getCurrentBoard() {
    return myCurrentBoard;
  }

  public void setCurrentBoard(TrelloBoard currentBoard) {
    myCurrentBoard = currentBoard;
  }

  @Nullable
  public TrelloList getCurrentList() {
    return myCurrentList;
  }

  public void setCurrentList(TrelloList currentList) {
    myCurrentList = currentList;
  }

  /**
   * Add authorization token and developer key in any request to Trello
   */
  @Override
  protected void configureHttpMethod(HttpMethod method) {
    if (StringUtil.isEmpty(myPassword)) {
       return;
    }
    String params = EncodingUtil.formUrlEncode(new NameValuePair[]{
      new NameValuePair("token", myPassword),
      new NameValuePair("key", TrelloRepositoryType.DEVELOPER_KEY)
    }, "utf-8");
    String oldParams = method.getQueryString();
    method.setQueryString(StringUtil.isEmpty(oldParams) ? params : oldParams + "&" + params);
  }

  @Nullable
  @Override
  public String extractId(String taskName) {
    return TrelloUtil.TRELLO_ID_PATTERN.matcher(taskName).matches() ? taskName : null;
  }

  /**
   * Request user information using supplied authorization token
   */
  @NotNull
  public TrelloUser fetchUserByToken() throws Exception {
    try {
      String url = TrelloUtil.TRELLO_API_BASE_URL + "/members/me";
      return makeRequestAndDeserializeJsonResponse(url, TrelloUser.class);
    }
    catch (Exception e) {
      LOG.warn("Error while fetching initial user info", e);
      // invalidate board and list if user can't be found
      myCurrentBoard = null;
      myCurrentList = null;
      throw e;
    }
  }

  @NotNull
  public TrelloBoard fetchBoardById(@NotNull String id) throws Exception {
    String url = TrelloUtil.TRELLO_API_BASE_URL + "/boards/" + id;
    try {
      return makeRequestAndDeserializeJsonResponse(url, TrelloBoard.class);
    }
    catch (Exception e) {
      LOG.warn("Error while fetching initial board info", e);
      throw e;
    }
  }

  @NotNull
  public TrelloList fetchListById(@NotNull String id) throws Exception {
    String url = TrelloUtil.TRELLO_API_BASE_URL + "/lists/" + id;
    try {
      return makeRequestAndDeserializeJsonResponse(url, TrelloList.class);
    }
    catch (Exception e) {
      LOG.warn("Error while fetching initial list info" + id, e);
      throw e;
    }
  }

  @NotNull
  public List<TrelloList> fetchBoardLists() throws Exception {
    if (myCurrentBoard == null) {
      throw new IllegalStateException("Board not set");
    }
    String url = TrelloUtil.TRELLO_API_BASE_URL + "/boards/" + myCurrentBoard.getId() + "/lists";
    return makeRequestAndDeserializeJsonResponse(url, TrelloUtil.LIST_OF_LISTS_TYPE);
  }

  @NotNull
  public List<TrelloBoard> fetchUserBoards() throws Exception {
    if (myCurrentUser == null) {
      throw new IllegalStateException("User not set");
    }
    String url = TrelloUtil.TRELLO_API_BASE_URL + "/members/me/boards?filter=open";
    return makeRequestAndDeserializeJsonResponse(url, TrelloUtil.LIST_OF_BOARDS_TYPE);
  }

  @NotNull
  private List<TrelloCard> fetchCards() throws Exception {
    boolean fromList = false;
    // choose most appropriate card provider
    String baseUrl;
    if (myCurrentList != null) {
      baseUrl = TrelloUtil.TRELLO_API_BASE_URL + "/lists/" + myCurrentList.getId() + "/cards";
      fromList = true;
    }
    else if (myCurrentBoard != null) {
      baseUrl = TrelloUtil.TRELLO_API_BASE_URL + "/boards/" + myCurrentBoard.getId() + "/cards";
    }
    else if (myCurrentUser != null) {
      baseUrl = TrelloUtil.TRELLO_API_BASE_URL + "/members/me/cards";
    }
    else {
      throw new IllegalStateException("Not configured");
    }
    String allCardsUrl = baseUrl + "?actions=commentCard&filter=all";
    List<TrelloCard> cards = makeRequestAndDeserializeJsonResponse(allCardsUrl, TrelloUtil.LIST_OF_CARDS_TYPE);
    LOG.debug("Total " + cards.size() + " cards downloaded");
    List<TrelloCard> filtered = ContainerUtil.filter(cards, new Condition<TrelloCard>() {
      @Override
      public boolean value(TrelloCard card) {
        return card.getIdMembers().contains(myCurrentUser.getId());
      }
    });
    LOG.debug("Total " + filtered.size() + " cards after filtering");
    if (!fromList) {
      // fix for IDEA-111470 and IDEA-111475
      // Select IDs of visible cards, e.d. cards that either archived explicitly, belong to archived list or closed board.
      // This information can't be extracted from single card description, because its 'closed' field
      // reflects only the card state and doesn't show state of parental list and board.
      // NOTE: According to Trello REST API "filter=visible" parameter may be used only when fetching cards for
      // particular board or user.
      String visibleCardsUrl = baseUrl + "?filter=visible&fields=none";
      List<TrelloCard> visibleCards = makeRequestAndDeserializeJsonResponse(visibleCardsUrl, TrelloUtil.LIST_OF_CARDS_TYPE);
      LOG.debug("Total " + visibleCards.size() + " visible cards");
      Set<String> visibleCardsIDs = ContainerUtil.map2Set(visibleCards, new Function<TrelloCard, String>() {
        @Override
        public String fun(TrelloCard card) {
          return card.getId();
        }
      });
      for (TrelloCard card : filtered) {
        card.setVisible(visibleCardsIDs.contains(card.getId()));
      }
    }
    return filtered;
  }

  /**
   * Make GET request to specified URL and return HTTP entity of result as Reader object
   */
  private String makeRequest(String url) throws Exception {
    HttpMethod method = new GetMethod(url);
    configureHttpMethod(method);
    return executeMethod(method);
  }

  private String executeMethod(HttpMethod method) throws Exception {
    HttpClient client = getHttpClient();
    String entityContent;
    try {
      client.executeMethod(method);
      // Can't use HttpMethod#getResponseBodyAsString because Trello doesn't specify encoding
      // in Content-Type header and by default this method decodes from Latin-1
      entityContent = StreamUtil.readText(method.getResponseBodyAsStream(), "utf-8");
    }
    finally {
      method.releaseConnection();
    }
    LOG.debug(entityContent);
    if (method.getStatusCode() != HttpStatus.SC_OK) {
      Header header = method.getResponseHeader("Content-Type");
      if (header != null && header.getValue().startsWith("text/plain")) {
        throw new Exception("Request failed. Reason: " + StringUtil.capitalize(entityContent));
      }
      throw new Exception("Request failed with HTTP error: " + method.getStatusText());
    }
    //return new InputStreamReader(method.getResponseBodyAsStream(), "utf-8");
    return entityContent;
  }

  @NotNull
  private <T> T makeRequestAndDeserializeJsonResponse(String url, Type type) throws Exception {
    String entityStream = makeRequest(url);
    // javac 1.6.0_23 bug workaround
    // TrelloRepository.java:286: type parameters of <T>T cannot be determined; no unique maximal instance exists for type variable T with upper bounds T,java.lang.Object
    //noinspection unchecked
    return (T)TrelloUtil.GSON.fromJson(entityStream, type);
  }

  @NotNull
  private <T> T makeRequestAndDeserializeJsonResponse(String url, Class<T> cls) throws Exception {
    String entityStream = makeRequest(url);
    return TrelloUtil.GSON.fromJson(entityStream, cls);
  }

  @Override
  public String getPresentableName() {
    String pseudoUrl = "trello.com";
    if (myCurrentBoard != null) {
      pseudoUrl += "/" + myCurrentBoard.getName();
    }
    if (myCurrentList != null) {
      pseudoUrl += "/" + myCurrentList.getName();
    }
    return pseudoUrl;
  }

  @Nullable
  @Override
  public CancellableConnection createCancellableConnection() {
    GetMethod method = new GetMethod(TrelloUtil.TRELLO_API_BASE_URL + "/members/me/cards?limit=1");
    configureHttpMethod(method);
    return new HttpTestConnection<GetMethod>(method) {
      @Override
      protected void doTest(GetMethod method) throws Exception {
        executeMethod(method);
      }
    };
  }

  @Override
  public boolean isConfigured() {
    return super.isConfigured() && StringUtil.isNotEmpty(myPassword);
  }

  @Override
  public String getUrl() {
    return "trello.com";
  }

  @Override
  protected int getFeatures() {
    return super.getFeatures() & ~NATIVE_SEARCH;
  }
}

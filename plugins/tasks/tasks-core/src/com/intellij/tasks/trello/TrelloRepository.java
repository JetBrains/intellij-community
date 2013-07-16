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
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.util.EncodingUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.util.List;

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
      throw new IllegalStateException("Authorization token not set");
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

  @Nullable
  private TrelloBoard fetchBoardById(String id) {
    String url = "/boards/" + id;
    try {
      return makeRequestAndDeserializeJsonResponse(url, TrelloBoard.class);
    }
    catch (Exception e) {
      LOG.warn("Error while fetching initial board info with id = " + id, e);
    }
    return null;
  }

  @Nullable
  private TrelloList fetchListById(String id) {
    String url = "/lists/" + id;
    try {
      return makeRequestAndDeserializeJsonResponse(url, TrelloList.class);
    }
    catch (Exception e) {
      LOG.warn("Error while fetching initial list info with id = " + id, e);
    }
    return null;
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
    // choose most appropriate card provider
    String url;
    if (myCurrentList != null) {
      url = TrelloUtil.TRELLO_API_BASE_URL + "/lists/" + myCurrentList.getId() + "/cards?actions=commentCard&filter=all";
    }
    else if (myCurrentBoard != null) {
      url = TrelloUtil.TRELLO_API_BASE_URL + "/boards/" + myCurrentBoard.getId() + "/cards?actions=commentCard&filter=all";
    }
    else if (myCurrentUser != null) {
      url = TrelloUtil.TRELLO_API_BASE_URL + "/members/me/cards/?actions=commentCard&filter=all";
    }
    else {
      throw new IllegalStateException("Not configured");
    }
    List<TrelloCard> cards = makeRequestAndDeserializeJsonResponse(url, TrelloUtil.LIST_OF_CARDS_TYPE);
    LOG.debug("Total " + cards.size() + " cards downloaded");
    List<TrelloCard> filtered = ContainerUtil.filter(cards, new Condition<TrelloCard>() {
      @Override
      public boolean value(TrelloCard card) {
        return card.getIdMembers().contains(myCurrentUser.getId());
      }
    });
    LOG.debug("Total " + filtered.size() + " cards after filtering");
    return filtered;
  }

  /**
   * Make GET request to specified URL and return HTTP entity of result as Reader object
   */
  private Reader makeRequest(String url) throws IOException {
    HttpClient client = getHttpClient();
    HttpMethod method = new GetMethod(url);
    configureHttpMethod(method);
    client.executeMethod(method);
    // Can't use HttpMethod#getResponseBodyAsString because Trello doesn't specify encoding
    // in Content-Type header and by default this method decodes from Latin-1
    String entityContent = StreamUtil.readText(method.getResponseBodyAsStream(), "utf-8");
    LOG.debug(entityContent);
    //return new InputStreamReader(method.getResponseBodyAsStream(), "utf-8");
    return new StringReader(entityContent);
  }

  @NotNull
  private <T> T makeRequestAndDeserializeJsonResponse(String url, Type type) throws IOException {
    Reader entityStream = makeRequest(url);
    try {
      // javac 1.6.0_23 bug workaround
      // TrelloRepository.java:286: type parameters of <T>T cannot be determined; no unique maximal instance exists for type variable T with upper bounds T,java.lang.Object
      //noinspection unchecked
      return (T)TrelloUtil.GSON.fromJson(entityStream, type);
    }
    finally {
      entityStream.close();
    }
  }

  @NotNull
  private <T> T makeRequestAndDeserializeJsonResponse(String url, Class<T> cls) throws IOException {
    Reader entityStream = makeRequest(url);
    try {
      return TrelloUtil.GSON.fromJson(entityStream, cls);
    }
    finally {
      entityStream.close();
    }
  }

  @Override
  public String getUrl() {
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
    // try to fetch user info to check connection availability
    GetMethod method = new GetMethod(TrelloUtil.TRELLO_API_BASE_URL + "/members/me");
    configureHttpMethod(method);
    return new HttpTestConnection<GetMethod>(method) {
      @Override
      protected void doTest(GetMethod method) throws Exception {
        int statusCode = getHttpClient().executeMethod(method);
        if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
          throw new Exception("Invalid token value");
        } else if (statusCode != HttpStatus.SC_OK) {
          throw new Exception("Error while connecting to server: " + HttpStatus.getStatusText(statusCode));
        }
      }
    };
  }
}

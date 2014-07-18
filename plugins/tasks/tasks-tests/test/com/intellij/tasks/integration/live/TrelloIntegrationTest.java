package com.intellij.tasks.integration.live;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.trello.TrelloRepository;
import com.intellij.tasks.trello.TrelloRepositoryType;
import com.intellij.tasks.trello.model.*;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class TrelloIntegrationTest extends LiveIntegrationTestCase<TrelloRepository> {

  private static final String BOARD_1_NAME = "Board 1";
  private static final String BOARD_1_ID = "53c416a8a6e5a78753562043";

  private static final String LIST_1_1_NAME = "List 1-1";
  private static final String LIST_1_1_ID = "53c416a8a6e5a78753562044";

  private static final String CARD_1_1_1_NAME = "Card 1-1-1";
  private static final String CARD_1_1_1_ID = "53c416d8b4bd36fb078446e5";

  @Override
  protected TrelloRepository createRepository() throws Exception {
    TrelloRepository repository = new TrelloRepository(new TrelloRepositoryType());
    String token = System.getProperty("tasks.tests.trello.token");
    if (StringUtil.isEmpty(token)) {
      throw new AssertionError("Authorization token is not set");
    }
    repository.setPassword(token);
    TrelloUser user = repository.fetchUserByToken();
    assertNotNull(user);
    repository.setCurrentUser(user);
    return repository;
  }

  // TODO Check closed tasks exclusion
  // TODO Check various cards visibility corner cases

  public void testFetchBoard() throws Exception {
    TrelloBoard board = myRepository.fetchBoardById(BOARD_1_ID);
    assertNotNull(board);
    assertEquals(BOARD_1_NAME, board.getName());
  }

  public void testFetchList() throws Exception {
    TrelloList list = myRepository.fetchListById(LIST_1_1_ID);
    assertNotNull(list);
    assertEquals(LIST_1_1_NAME, list.getName());
  }

  public void testFetchCard() throws Exception {
    TrelloCard card = myRepository.fetchCardById(CARD_1_1_1_ID);
    assertNotNull(card);
    assertEquals(CARD_1_1_1_NAME, card.getName());
  }

  public void testFetchBoardsOfUser() throws Exception {
    List<TrelloBoard> boards = myRepository.fetchUserBoards();
    assertEquals(2, boards.size());
    assertObjectsNamed("All boards of the user should be included", boards, "Board 1", "Board 2");
  }

  public void testFetchListsOfBoard() throws Exception {
    TrelloBoard selectedBoard = myRepository.fetchBoardById(BOARD_1_ID);
    assertNotNull(selectedBoard);
    myRepository.setCurrentBoard(selectedBoard);
    List<TrelloList> lists = myRepository.fetchBoardLists();
    assertEquals(3, lists.size());
    assertObjectsNamed("All lists of the board should be included", lists, "List 1-1", "List 1-2", "List 1-3");
  }

  @NotNull
  private List<TrelloCard> fetchCards(@Nullable String boardId, @Nullable String listId, boolean withClosed) throws Exception {
    if (boardId != null) {
      TrelloBoard selectedBoard = myRepository.fetchBoardById(BOARD_1_ID);
      assertNotNull(selectedBoard);
      myRepository.setCurrentBoard(selectedBoard);
    }
    if (listId != null) {
      TrelloList selectedList = myRepository.fetchListById(LIST_1_1_ID);
      assertNotNull(selectedList);
      myRepository.setCurrentList(selectedList);
    }
    return myRepository.fetchCards(100, withClosed);
  }

  public void testFetchingCardsOfUser() throws Exception {
    myRepository.setIncludeAllCards(true);
    List<TrelloCard> cards = fetchCards(null, null, true);
    assertObjectsNamed("All cards assigned to user should be included", cards, "Card 1-1-1");
  }

  public void testFetchingCardsOfBoard() throws Exception {
    myRepository.setIncludeAllCards(true);
    List<TrelloCard> cards = fetchCards(BOARD_1_ID, null, true);
    assertObjectsNamed("All cards of the board should be included",
                       cards, "Card 1-1-1", "Card 1-1-2", "Card 1-2-1", "Card 1-3-1", "Archived Card");
  }

  public void testCardsFilteringByMembership() throws Exception {
    myRepository.setIncludeAllCards(true);
    List<TrelloCard> allCards = fetchCards(BOARD_1_ID, LIST_1_1_ID, true);
    assertObjectsNamed("All cards of the list should be included", allCards, "Card 1-1-1", "Card 1-1-2", "Archived Card");

    myRepository.setIncludeAllCards(false);
    List<TrelloCard> assignedCards = fetchCards(BOARD_1_ID, LIST_1_1_ID, true);
    assertObjectsNamed("Only cards of the list assigned to user should be included", assignedCards, "Card 1-1-1");
  }

  public void testCardsFilteringByStatus() throws Exception {
    myRepository.setIncludeAllCards(true);
    List<TrelloCard> allCards = fetchCards(BOARD_1_ID, LIST_1_1_NAME, true);
    assertObjectsNamed("All cards of the list should be included", allCards, "Card 1-1-1", "Card 1-1-2", "Archived Card");

    TrelloCard card = ContainerUtil.find(allCards, new Condition<TrelloCard>() {
      @Override
      public boolean value(TrelloCard card) {
        return card.getName().equals("Archived Card");
      }
    });
    assertNotNull(card);
    assertTrue(card.isClosed());
    assertFalse(card.isVisible());
  }

  static void assertObjectsNamed(@NotNull String message, @NotNull Collection<? extends TrelloModel> objects, @NotNull String... names) {
    assertEquals(message, ContainerUtil.newHashSet(names), ContainerUtil.map2Set(objects, new Function<TrelloModel, String>() {
      @Override
      public String fun(TrelloModel model) {
        return model.getName();
      }
    }));
  }
}

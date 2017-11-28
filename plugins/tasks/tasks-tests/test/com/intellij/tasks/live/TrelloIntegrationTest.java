package com.intellij.tasks.live;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.CustomTaskState;
import com.intellij.tasks.trello.TrelloRepository;
import com.intellij.tasks.trello.TrelloRepositoryType;
import com.intellij.tasks.trello.TrelloTask;
import com.intellij.tasks.trello.model.*;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.intellij.tasks.trello.model.TrelloLabel.LabelColor.*;

/**
 * @author Mikhail Golubev
 */
public class TrelloIntegrationTest extends LiveIntegrationTestCase<TrelloRepository> {

  // Basic functionality (searching, filtering, etc.)
  private static final String BASIC_FUNCTIONALITY_BOARD_NAME = "Basic Functionality";
  private static final String BASIC_FUNCTIONALITY_BOARD_ID = "53c416a8a6e5a78753562043";

  private static final String LIST_1_1_NAME = "List 1-1";
  private static final String LIST_1_1_ID = "53c416a8a6e5a78753562044";

  private static final String CARD_1_1_1_NAME = "Card 1-1-1";
  private static final String CARD_1_1_1_ID = "53c416d8b4bd36fb078446e5";
  private static final String CARD_1_1_1_NUMBER = "1";

  // Labels and colors
  private static final String LABELS_AND_COLORS_BOARD_NAME = "Labels and Colors";
  private static final String COLORED_CARD_ID = "548591e00f3d598512ced37b";
  private static final String CARD_WITH_COLORLESS_LABELS_ID = "5714ccd9f6ab69c4de522346";
  
  // State updates
  private static final String STATE_UPDATES_BOARD_NAME = "State Updates";
  private static final String STATE_UPDATES_BOARD_ID = "54b3e0e3b4f415b3c9d03449";
  private static final String BACKLOG_LIST_ID = "54b3e0e849e831746351e063";
  private static final String IN_PROGRESS_LIST_ID = "54b3e0ebf5035aaddcbe15b4";
  private static final String FEATURE_CARD_ID = "54b3e0efed4db033b634cd39";

  @Override
  protected TrelloRepository createRepository() throws Exception {
    try {
      TrelloRepository repository = new TrelloRepository(new TrelloRepositoryType());
      String token = System.getProperty("tasks.tests.trello.token");
      assertTrue("Authorization token is not set", !StringUtil.isEmpty(token));
      repository.setPassword(token);
      TrelloUser user = repository.fetchUserByToken();
      assertNotNull(user);
      repository.setCurrentUser(user);
      return repository;
    }
    catch (AssertionError ae){
      tearDown();
      throw ae;
    }
  }

  // TODO Check closed tasks exclusion
  // TODO Check various cards visibility corner cases

  public void testFetchBoard() throws Exception {
    TrelloBoard board = myRepository.fetchBoardById(BASIC_FUNCTIONALITY_BOARD_ID);
    assertNotNull(board);
    assertEquals(BASIC_FUNCTIONALITY_BOARD_NAME, board.getName());
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
    assertEquals(3, boards.size());
    assertObjectsNamed("All boards of the user should be included", boards,
                       BASIC_FUNCTIONALITY_BOARD_NAME, LABELS_AND_COLORS_BOARD_NAME, STATE_UPDATES_BOARD_NAME);
  }

  public void testFetchListsOfBoard() throws Exception {
    TrelloBoard selectedBoard = myRepository.fetchBoardById(BASIC_FUNCTIONALITY_BOARD_ID);
    assertNotNull(selectedBoard);
    myRepository.setCurrentBoard(selectedBoard);
    List<TrelloList> lists = myRepository.fetchBoardLists();
    assertEquals(3, lists.size());
    assertObjectsNamed("All lists of the board should be included", lists, "List 1-1", "List 1-2", "List 1-3");
  }

  @NotNull
  private List<TrelloCard> fetchCards(@Nullable String boardId, @Nullable String listId, boolean withClosed) throws Exception {
    if (boardId != null) {
      TrelloBoard selectedBoard = myRepository.fetchBoardById(BASIC_FUNCTIONALITY_BOARD_ID);
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
    List<TrelloCard> cards = fetchCards(BASIC_FUNCTIONALITY_BOARD_ID, null, true);
    assertObjectsNamed("All cards of the board should be included",
                       cards, "Card 1-1-1", "Card 1-1-2", "Card 1-2-1", "Card 1-3-1", "Archived Card");
  }

  public void testCardsFilteringByMembership() throws Exception {
    myRepository.setIncludeAllCards(true);
    List<TrelloCard> allCards = fetchCards(BASIC_FUNCTIONALITY_BOARD_ID, LIST_1_1_ID, true);
    assertObjectsNamed("All cards of the list should be included", allCards, "Card 1-1-1", "Card 1-1-2", "Archived Card");

    myRepository.setIncludeAllCards(false);
    List<TrelloCard> assignedCards = fetchCards(BASIC_FUNCTIONALITY_BOARD_ID, LIST_1_1_ID, true);
    assertObjectsNamed("Only cards of the list assigned to user should be included", assignedCards, "Card 1-1-1");
  }

  public void testCardsFilteringByStatus() throws Exception {
    myRepository.setIncludeAllCards(true);
    List<TrelloCard> allCards = fetchCards(BASIC_FUNCTIONALITY_BOARD_ID, LIST_1_1_NAME, true);
    assertObjectsNamed("All cards of the list should be included", allCards, "Card 1-1-1", "Card 1-1-2", "Archived Card");

    TrelloCard card = ContainerUtil.find(allCards, card1 -> card1.getName().equals("Archived Card"));
    assertNotNull(card);
    assertTrue(card.isClosed());
    assertFalse(card.isVisible());
  }

  public void testTestConnection() {
    assertNull(myRepository.createCancellableConnection().call());

    myRepository.setPassword("illegal password");
    final Exception error = myRepository.createCancellableConnection().call();
    assertNotNull(error);
    assertTrue(error.getMessage().contains("Unauthorized"));
  }

  public void testLabelsAndColors() throws Exception {
    final TrelloCard card = myRepository.fetchCardById(COLORED_CARD_ID);
    assertNotNull(card);
    final List<TrelloLabel> labels = card.getLabels();

    assertEquals(6, labels.size());
    final Set<String> labelNames = ContainerUtil.map2Set(labels, TrelloLabel::getName);
    assertSameElements(labelNames, "Sky colored label", "Boring label", "Dull label", "");

    assertEquals(EnumSet.of(SKY, LIME, PINK, BLACK), card.getColors());
  }
  
  // EA-81551
  public void testAllLabelsWithoutColor() throws Exception {
    final TrelloCard card = myRepository.fetchCardById(CARD_WITH_COLORLESS_LABELS_ID);
    assertNotNull(card);
    final List<TrelloLabel> labels = card.getLabels();
    assertSize(2, labels);
    final List<String> labelNames = ContainerUtil.map(labels, TrelloLabel::getName);
    assertSameElements(labelNames, "Boring label", "Dull label");
    assertEmpty(card.getColors());
  }

  public void testStateUpdates() throws Exception {
    TrelloCard card = myRepository.fetchCardById(FEATURE_CARD_ID);
    assertNotNull(card);
    assertEquals(STATE_UPDATES_BOARD_ID, card.getIdBoard());
    assertEquals(BACKLOG_LIST_ID, card.getIdList());

    // Discover "In Progress" list
    TrelloTask task = new TrelloTask(card, myRepository);
    Set<CustomTaskState> states = myRepository.getAvailableTaskStates(task);
    assertEquals(1, states.size());
    final CustomTaskState inProgressState = states.iterator().next();
    assertEquals(IN_PROGRESS_LIST_ID, inProgressState.getId());
    assertEquals("In Progress", inProgressState.getPresentableName());

    // Backlog -> In Progress
    myRepository.setTaskState(task, inProgressState);
    card = myRepository.fetchCardById(FEATURE_CARD_ID);
    assertNotNull(card);
    assertEquals(STATE_UPDATES_BOARD_ID, card.getIdBoard());
    assertEquals(IN_PROGRESS_LIST_ID, card.getIdList());

    // Discover "Backlog" list
    task = new TrelloTask(card, myRepository);
    states = myRepository.getAvailableTaskStates(task);
    assertEquals(1, states.size());
    final CustomTaskState backlogState = states.iterator().next();
    assertEquals(BACKLOG_LIST_ID, backlogState.getId());
    assertEquals("Backlog", backlogState.getPresentableName());

    // In Progress -> Backlog
    myRepository.setTaskState(task, backlogState);
    card = myRepository.fetchCardById(FEATURE_CARD_ID);
    assertNotNull(card);
    assertEquals(STATE_UPDATES_BOARD_ID, card.getIdBoard());
    assertEquals(BACKLOG_LIST_ID, card.getIdList());
  }

  // IDEA-139903
  public void testCardBoardLocalNumber() throws Exception {
    final TrelloCard card = myRepository.fetchCardById(CARD_1_1_1_ID);
    assertNotNull(card);
    assertEquals(CARD_1_1_1_ID, card.getId());
    assertEquals(CARD_1_1_1_NUMBER, new TrelloTask(card, myRepository).getNumber());
  }

  private static void assertObjectsNamed(@NotNull String message,
                                         @NotNull Collection<? extends TrelloModel> objects,
                                         @NotNull String... names) {
    assertEquals(message, ContainerUtil.newHashSet(names), ContainerUtil.map2Set(objects,
                                                                                 (Function<TrelloModel, String>)model -> model.getName()));
  }
}

package com.intellij.driver.impl;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.ModalityStateEx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EdtCallDispatcherTest {
  private final FakeEdt edt = new FakeEdt();
  private final ExecutorService pool = Executors.newCachedThreadPool();

  @AfterEach
  void tearDown() {
    edt.closeDialog();
    edt.close();
    pool.shutdownNow();
  }

  @Test
  void aCallQueuedBeforeAModalDialogOpensRunsInTheDialog() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    AtomicBoolean opened = new AtomicBoolean();
    // the dialog opens after the call has read the current modality and before its queued copy runs
    BiConsumer<Runnable, ModalityState> dispatch = (r, modality) -> {
      if (opened.compareAndSet(false, true)) {
        edt.openDialog();
      }
      edt.invokeAndWait(r, modality);
    };

    CompletableFuture<Void> call = CompletableFuture.runAsync(
      () -> EdtCallDispatcher.invokeAndWait(calls::incrementAndGet, edt::currentModality, dispatch, pool), pool);

    call.get(5, TimeUnit.SECONDS);
    assertEquals(1, calls.get());
    assertTrue(edt.isDialogOpen());

    // the copy that the dialog held does nothing when it runs after the dialog closes
    edt.closeDialog();
    edt.awaitQueueEmpty();
    assertEquals(1, calls.get());
  }

  @Test
  void aCallWithoutAModalityChangeIsQueuedOnce() {
    AtomicInteger calls = new AtomicInteger();
    AtomicInteger dispatches = new AtomicInteger();

    EdtCallDispatcher.invokeAndWait(calls::incrementAndGet, edt::currentModality, (r, modality) -> {
      dispatches.incrementAndGet();
      edt.invokeAndWait(r, modality);
    }, pool);

    assertEquals(1, calls.get());
    assertEquals(1, dispatches.get());
  }

  @Test
  void aCallInAnOpenDialogRunsInIt() {
    AtomicInteger calls = new AtomicInteger();
    edt.openDialog();

    EdtCallDispatcher.invokeAndWait(calls::incrementAndGet, edt::currentModality, edt::invokeAndWait, pool);

    assertEquals(1, calls.get());
  }

  @Test
  void aFailingCallFailsWithTheErrorOfItsDispatch() {
    IllegalStateException boom = new IllegalStateException("boom");
    RuntimeException[] dispatched = new RuntimeException[1];

    RuntimeException thrown = assertThrows(RuntimeException.class, () ->
      EdtCallDispatcher.invokeAndWait(() -> { throw boom; }, edt::currentModality, (r, modality) -> {
        try {
          edt.invokeAndWait(r, modality);
        }
        catch (RuntimeException e) {
          dispatched[0] = e;
          throw e;
        }
      }, pool));

    assertSame(dispatched[0], thrown);
    assertSame(boom, thrown.getCause());
  }

  @Test
  void aDispatchThatFailsBeforeTheCallRunsFailsTheCall() {
    AtomicInteger calls = new AtomicInteger();
    IllegalStateException refused = new IllegalStateException("Calling invokeAndWait from read-action leads to possible deadlock.");

    IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
      EdtCallDispatcher.invokeAndWait(calls::incrementAndGet, edt::currentModality, (r, modality) -> { throw refused; }, pool));

    assertSame(refused, thrown);
    assertEquals(0, calls.get());
  }

  /**
   * An EDT that runs queued runnables as LaterInvocator does, only when the current modality accepts theirs, and reads in any
   * modality, as SwingUtilities.invokeAndWait does.
   */
  private static final class FakeEdt implements AutoCloseable {
    private record Queued(Runnable runnable, ModalityState modality) {
    }

    private final Object lock = new Object();
    private final List<Queued> queue = new ArrayList<>();
    private final Deque<Runnable> reads = new ArrayDeque<>();
    private final Object dialog = new Object();
    private ModalityState current = ModalityState.nonModal();
    private boolean running;
    private boolean closed;

    FakeEdt() {
      Thread thread = new Thread(this::loop, "fake EDT");
      thread.setDaemon(true);
      thread.start();
    }

    private void loop() {
      while (true) {
        Runnable next;
        synchronized (lock) {
          running = false;
          lock.notifyAll();
          while ((next = next()) == null) {
            if (closed) {
              return;
            }
            try {
              lock.wait();
            }
            catch (InterruptedException e) {
              return;
            }
          }
          running = true;
        }
        next.run();
      }
    }

    private Runnable next() {
      if (!reads.isEmpty()) {
        return reads.poll();
      }
      for (Iterator<Queued> it = queue.iterator(); it.hasNext(); ) {
        Queued queued = it.next();
        if (current.accepts(queued.modality())) {
          it.remove();
          return queued.runnable();
        }
      }
      return null;
    }

    /** As Application.invokeAndWait, which reports a failure of the runnable wrapped in a RuntimeException. */
    void invokeAndWait(Runnable runnable, ModalityState modality) {
      CompletableFuture<Void> done = new CompletableFuture<>();
      synchronized (lock) {
        queue.add(new Queued(() -> {
          try {
            runnable.run();
            done.complete(null);
          }
          catch (Throwable e) {
            done.completeExceptionally(e);
          }
        }, modality));
        lock.notifyAll();
      }
      try {
        done.get();
      }
      catch (Exception e) {
        throw new RuntimeException(e.getCause());
      }
    }

    /** As SwingUtilities.invokeAndWait with ModalityState.current(). */
    ModalityState currentModality() {
      return read(() -> current);
    }

    private <T> T read(Supplier<T> reader) {
      CompletableFuture<T> result = new CompletableFuture<>();
      synchronized (lock) {
        reads.add(() -> result.complete(reader.get()));
        lock.notifyAll();
      }
      return result.join();
    }

    void openDialog() {
      synchronized (lock) {
        current = new ModalityStateEx(Collections.singletonList(dialog));
        lock.notifyAll();
      }
    }

    void closeDialog() {
      synchronized (lock) {
        current = ModalityState.nonModal();
        lock.notifyAll();
      }
    }

    boolean isDialogOpen() {
      synchronized (lock) {
        return current != ModalityState.nonModal();
      }
    }

    void awaitQueueEmpty() throws InterruptedException {
      synchronized (lock) {
        while (!queue.isEmpty() || running) {
          lock.wait();
        }
      }
    }

    @Override
    public void close() {
      synchronized (lock) {
        closed = true;
        lock.notifyAll();
      }
    }
  }
}

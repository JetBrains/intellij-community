package org.jetbrains.debugger.memory.tracking;

import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.jdi.ThreadReferenceProxy;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

class HashBasedTracking extends InstanceTrackingStrategy {
  private static final Logger LOG = Logger.getInstance(HashBasedTracking.class);

  private final ReferenceType myClassType;

  private MyHashCodeMethodWrapper myHashCodeWrapper;
  private Set<Integer> myHashesSet;
  private MyState myState = MyState.WAIT_HASHES;
  private int myCount = 0;
  HashBasedTracking(@Nullable SuspendContextImpl suspendContext,
                    @NotNull ReferenceType classType,
                    @NotNull List<ObjectReference> initialInstances) {
    myClassType = classType;
    if (suspendContext != null) {
      myHashesSet = toSetOfHashes(evalHashCodes(suspendContext, initialInstances));
      myState = myState.next();
    }
  }

  @NotNull
  @Override
  protected List<ObjectReference> updateImpl(@NotNull SuspendContextImpl suspendContext,
                                             @NotNull List<ObjectReference> references) {
    Map<ObjectReference, Optional<Integer>> ref2hash = evalHashCodes(suspendContext, references);
    List<ObjectReference> newInstances = new ArrayList<>();

    for (ObjectReference ref : references) {
      Optional<Integer> hash = ref2hash.get(ref);
      if (hash.isPresent() && !myState.equals(MyState.WAIT_HASHES) && !myHashesSet.contains(hash.get())) {
        newInstances.add(ref);
      }
    }

    myHashesSet = toSetOfHashes(ref2hash);
    myState = myState.next();
    myCount = newInstances.size();
    return newInstances;
  }

  @Override
  public int getCount() {
    return myCount;
  }

  @Override
  public boolean isReady() {
    return myState == MyState.READY;
  }

  @NotNull
  private Map<ObjectReference, Optional<Integer>> evalHashCodes(@NotNull SuspendContextImpl suspendContext,
                                                                @NotNull List<ObjectReference> references) {
    MyHashCodeMethodWrapper hashCodeCaller = getHashCodeMethod(myClassType);
    Map<ObjectReference, Optional<Integer>> result = new HashMap<>();
    ThreadReferenceProxy threadProxy = suspendContext.getThread();
    ThreadReference thread = threadProxy != null ? threadProxy.getThreadReference() : null;
    if (thread == null || hashCodeCaller == null) {
      LOG.warn(thread == null ? "Thread is null" : "hashCode not found");
      return result;
    }

    long start = System.nanoTime();
    for (ObjectReference ref : references) {
      Optional<Integer> hash = Optional.empty();
      try {
        if (!ref.isCollected()) {
          ref.disableCollection();
          if (!ref.isCollected()) {
            hash = hashCodeCaller.eval(ref, suspendContext, thread);
          }
          ref.enableCollection();
        }
      } catch (InvalidTypeException | ClassNotLoadedException |
          IncompatibleThreadStateException | InvocationException e) {
        LOG.warn("Hash code evaluation failed", e);
      }

      result.put(ref, hash);
    }

    long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    LOG.info(String.format("Hash code evaluation: %d ms. Count = %d", duration, references.size()));

    return result;
  }

  private Set<Integer> toSetOfHashes(@NotNull Map<ObjectReference, Optional<Integer>> ref2hashCode) {
    return ref2hashCode.values().stream()
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toCollection(HashSet::new));
  }

  @Nullable
  private MyHashCodeMethodWrapper getHashCodeMethod(@NotNull ReferenceType referenceType) {
    if (myHashCodeWrapper != null) {
      return myHashCodeWrapper;
    }

    final List<ReferenceType> referenceTypes = referenceType.virtualMachine().classesByName("java.util.Objects");
    if (!referenceTypes.isEmpty()) {
      ClassType objectsClass = (ClassType) referenceTypes.get(0);
      Method hashCodeFromObjects = objectsClass.methodsByName("hashCode").stream()
          .filter(method -> method.argumentTypeNames().size() == 1
              && "java.lang.Object".equals(method.argumentTypeNames().get(0)))
          .findFirst()
          .orElse(null);

      myHashCodeWrapper = (ref, suspendContext, thread) -> {
        final Value value = objectsClass.invokeMethod(thread, hashCodeFromObjects,
            Collections.singletonList(ref), ClassType.INVOKE_SINGLE_THREADED);
        return Optional.of(((IntegerValue) value).value());
      };
    } else {
      final Method hashCode = referenceType.methodsByName("hashCode").stream()
          .filter(method -> method.argumentTypeNames().isEmpty())
          .findFirst()
          .orElse(null);

      myHashCodeWrapper = hashCode == null ? null : (ref, suspendContext, thread) -> {
        final Value value = ref.invokeMethod(thread, hashCode, Collections.emptyList(), ClassType.INVOKE_SINGLE_THREADED);
        return Optional.of(((IntegerValue) value).value());
      };
    }

    return myHashCodeWrapper;
  }

  // WAIT_HASHES -> WAIT_UPDATE -> READY
  enum MyState {
    WAIT_HASHES {
      @Override
      public MyState next() {
        return WAIT_UPDATE;
      }
    }, WAIT_UPDATE {
      @Override
      public MyState next() {
        return READY;
      }
    }, READY {
      @Override
      public MyState next() {
        return this;
      }
    };

    public abstract MyState next();
  }

  private interface MyHashCodeMethodWrapper {
    Optional<Integer> eval(@NotNull ObjectReference ref, @NotNull SuspendContextImpl suspendContext,
                           @NotNull ThreadReference thread) throws InvocationException, InvalidTypeException,
        ClassNotLoadedException, IncompatibleThreadStateException;
  }
}

package org.jetbrains.debugger.memory.tracking;

import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.jdi.ThreadReferenceProxy;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

class HashBasedTracking extends InstanceTrackingStrategy {
  private final ReferenceType myClassType;
  private Set<Integer> myHashesSet;
  private MyState myState = MyState.WAIT_HASHES;

  HashBasedTracking(@Nullable SuspendContextImpl suspendContext,
                    @NotNull ReferenceType classType,
                    @NotNull List<ObjectReference> initialInstances) {
    myClassType = classType;
    if(suspendContext != null) {
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

    for(ObjectReference ref : references) {
      Optional<Integer> hash = ref2hash.get(ref);
      if(hash.isPresent() && !myHashesSet.contains(hash.get())) {
        newInstances.add(ref);
      }
    }

    myHashesSet = toSetOfHashes(ref2hash);
    myState = myState.next();
    return newInstances;
  }

  @Override
  public boolean isReady() {
    return myState == MyState.READY;
  }

  @NotNull
  private Map<ObjectReference, Optional<Integer>> evalHashCodes(@NotNull SuspendContextImpl suspendContext,
                                                                @NotNull List<ObjectReference> references) {
    Method hashCodeMethod = getHashCodeMethod(myClassType);
    Map<ObjectReference, Optional<Integer>> result = new HashMap<>();
    ThreadReferenceProxy threadProxy = suspendContext.getThread();
    ThreadReference thread = threadProxy != null ? threadProxy.getThreadReference() : null;
    if(thread == null || hashCodeMethod == null) {
      return result;
    }

    for (ObjectReference ref : references) {
      Optional<Integer> hash = Optional.empty();
      try {
        if(!ref.isCollected()) {
          ref.disableCollection();
          if(!ref.isCollected()) {
            Value value = ref.invokeMethod(thread, hashCodeMethod, Collections.emptyList(),
                ObjectReference.INVOKE_NONVIRTUAL);
            hash = Optional.of(((IntegerValue)value).value());
          }
          ref.enableCollection();
        }
      } catch (InvalidTypeException | ClassNotLoadedException |
          IncompatibleThreadStateException | InvocationException ignored) {
      }

      result.put(ref, hash);
    }

    return result;
  }

  private Set<Integer> toSetOfHashes(@NotNull Map<ObjectReference, Optional<Integer>> ref2hashCode) {
    return ref2hashCode.values().stream()
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toCollection(HashSet::new));
  }

  @Nullable
  private Method getHashCodeMethod(@Nullable ReferenceType referenceType) {
    if (referenceType == null) {
      return null;
    }

    return referenceType.methodsByName("hashCode").stream()
        .filter(method -> method.argumentTypeNames().isEmpty())
        .findFirst()
        .orElseGet(null);
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
}

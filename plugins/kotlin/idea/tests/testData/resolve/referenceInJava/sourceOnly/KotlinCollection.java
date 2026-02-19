import kotlin.collections.CollectionsKt;

public class KotlinReferrer {
    public void refer() {
        CollectionsKt.array<caret>ListOf(1);
    }
}

// REF: (kotlin.collections).arrayListOf(vararg T)
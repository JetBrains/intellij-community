import kotlin.annotations.jvm.ReadOnly;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

abstract public class MyJavaCLass {
    public abstract void col<caret>l(@ReadOnly @NotNull Collection<Integer> c, int i);
}
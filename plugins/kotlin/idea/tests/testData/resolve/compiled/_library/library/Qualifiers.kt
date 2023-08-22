import java.util.List
import java.util.Set
import java.util.Map
import java.util.HashSet

interface Qualifiers<T> : List<T> {
  fun <K: Set<T>> foo(p: Map<T, K>) : HashSet<T>
}
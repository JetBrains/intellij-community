import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class ItemsList<T> implements List<T> {
  private final List<T> items;
  private final String additionalData;

  ItemsList(List<T> items, String additionalData) {
    this.items = items;
    this.additionalData = additionalData;
  }

  public int size() {
    return this.items.size();
  }

  public boolean isEmpty() {
    return this.items.isEmpty();
  }

  public boolean contains(Object o) {
    return this.items.contains(o);
  }

  public Iterator<T> iterator() {
    return this.items.iterator();
  }

  public Object[] toArray() {
    return this.items.toArray();
  }

  public <T> T[] toArray(T[] ts) {
    return this.items.toArray(ts);
  }

  public boolean add(T e) {
    return this.items.add(e);
  }

  public boolean remove(Object o) {
    return this.items.remove(o);
  }

  public boolean containsAll(Collection<?> collection) {
    return this.items.containsAll(collection);
  }

  public boolean addAll(Collection<? extends T> collection) {
    return this.items.addAll(collection);
  }

  public boolean addAll(int i, Collection<? extends T> collection) {
    return this.items.addAll(i, collection);
  }

  public boolean removeAll(Collection<?> collection) {
    return this.items.removeAll(collection);
  }

  public boolean retainAll(Collection<?> collection) {
    return this.items.retainAll(collection);
  }

  public void replaceAll(UnaryOperator<T> operator) {
    this.items.replaceAll(operator);
  }

  public void sort(Comparator<? super T> c) {
    this.items.sort(c);
  }

  public void clear() {
    this.items.clear();
  }

  public T get(int i) {
    return this.items.get(i);
  }

  public T set(int i, T e) {
    return this.items.set(i, e);
  }

  public void add(int i, T e) {
    this.items.add(i, e);
  }

  public T remove(int i) {
    return this.items.remove(i);
  }

  public int indexOf(Object o) {
    return this.items.indexOf(o);
  }

  public int lastIndexOf(Object o) {
    return this.items.lastIndexOf(o);
  }

  public ListIterator<T> listIterator() {
    return this.items.listIterator();
  }

  public ListIterator<T> listIterator(int i) {
    return this.items.listIterator(i);
  }

  public List<T> subList(int i, int i1) {
    return this.items.subList(i, i1);
  }

  public Spliterator<T> spliterator() {
    return this.items.spliterator();
  }

  public void addFirst(T e) {
    this.items.addFirst(e);
  }

  public void addLast(T e) {
    this.items.addLast(e);
  }

  public T getFirst() {
    return this.items.getFirst();
  }

  public T getLast() {
    return this.items.getLast();
  }

  public T removeFirst() {
    return this.items.removeFirst();
  }

  public T removeLast() {
    return this.items.removeLast();
  }

  public List<T> reversed() {
    return this.items.reversed();
  }

  public <T> T[] toArray(IntFunction<T[]> generator) {
    return this.items.toArray(generator);
  }

  public boolean removeIf(Predicate<? super T> filter) {
    return this.items.removeIf(filter);
  }

  public Stream<T> stream() {
    return this.items.stream();
  }

  public Stream<T> parallelStream() {
    return this.items.parallelStream();
  }

  public void forEach(Consumer<? super T> action) {
    this.items.forEach(action);
  }
}

public class DelegateWithInterfaces {
  public List<String> test() {
    var list = new ItemsList<String>(Arrays.asList("S1", "S2"), "data");
    return list.stream().map(String::toLowerCase).collect(Collectors.toList());
  }
}
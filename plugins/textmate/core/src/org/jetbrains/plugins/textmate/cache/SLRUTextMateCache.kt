package org.jetbrains.plugins.textmate.cache

import org.jetbrains.plugins.textmate.createTextMateLock
import kotlin.concurrent.atomics.*
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@OptIn(ExperimentalTime::class)
class SLRUTextMateCache<K : Any, V : Any?>(
  capacity: Int,
  private val computeFn: (K) -> V,
  private val disposeFn: (V) -> Unit,
  protectedRatio: Double = 0.8,
  private val timeSource: TimeSource = TimeSource.Monotonic
) : TextMateCache<K, V> {

  private data class Entry<K, V>(
    val key: K,
    val value: V,
    var lastAccessed: AtomicReference<TimeMark>,
    val refCount: AtomicInt = AtomicInt(0),
    val evicted: AtomicBoolean = AtomicBoolean(false),
    var prev: Entry<K, V>? = null,
    var next: Entry<K, V>? = null,
  )

  private val protectedSize = maxOf(1, (capacity * protectedRatio).toInt())
  private val probationarySize = capacity - protectedSize

  private val probationaryCache = LRUSegment<K, V>()
  private val protectedCache = LRUSegment<K, V>()

  private val lock = createTextMateLock()

  init {
    require(capacity > 0) { "Capacity must be positive" }
    require(protectedRatio in 0.0..1.0) { "Protected ratio must be between 0 and 1" }
    require(protectedSize >= 0) { "Protected size must be positive" }
    require(probationarySize >= 0) { "Probationary size must be positive" }
  }

  private fun valueRef(entry: Entry<K, V>): TextMateCachedValue<V> {
    return object : TextMateCachedValue<V> {
      override val value: V
        get() = entry.value

      override fun close() {
        if (entry.refCount.decrementAndFetch() == 0 && entry.evicted.load()) {
          finalizeEviction(entry)
        }
      }
    }
  }

  override fun get(key: K): TextMateCachedValue<V> {
    val existingEntry: Entry<K, V>? = lock.withLock {
      protectedCache.get(key) ?: run {
        probationaryCache.get(key)?.also {
          promoteToProtected(it)
        }
      }?.takeIf { !it.evicted.load() }?.also { existingEntry ->
        existingEntry.refCount.incrementAndFetch()
        existingEntry.lastAccessed.store(timeSource.markNow())
      }
    }
    return if (existingEntry != null) {
      valueRef(existingEntry)
    }
    else {
      val newValue = computeFn(key)
      val newEntry = Entry(key = key,
                           value = newValue,
                           lastAccessed = AtomicReference(timeSource.markNow()),
                           refCount = AtomicInt(1))
      val (newOrExistingEntry, newEntryInserted) = lock.withLock {
        val doubleCheckedExistingEntry = protectedCache.get(key) ?: run {
          probationaryCache.get(key)?.also {
            promoteToProtected(it)
          }
        }?.takeIf { !it.evicted.load() }
        if (doubleCheckedExistingEntry != null) {
          doubleCheckedExistingEntry.refCount.incrementAndFetch()
          doubleCheckedExistingEntry.lastAccessed.store(timeSource.markNow())
          doubleCheckedExistingEntry to false
        }
        else {
          putToProbation(key, newEntry)
          newEntry to true
        }
      }
      if (!newEntryInserted) {
        disposeFn(newValue)
      }
      valueRef(newOrExistingEntry)
    }
  }

  private fun putToProbation(key: K, newEntry: Entry<K, V>) {
    // The proper implementation of SLRU should be `while (probationaryCache.size() >= probationarySize)`
    // but to avoid too much waste when a lot of unique accesses happen before anything is going to be promoted,
    // the entire capacity is used for eviction from probation
    while (size() >= probationarySize + protectedSize) {
      evictFromProbationary()
    }
    probationaryCache.put(key, newEntry)
  }

  private fun promoteToProtected(entry: Entry<K, V>) {
    while (protectedCache.size() >= protectedSize) {
      evictFromProtected()
    }
    if (probationaryCache.remove(entry.key) != null) {
      protectedCache.put(entry.key, entry)
    }
  }

  private fun evictFromProtected() {
    val victim = protectedCache.removeEldest() ?: return
    // if refCount is not zero, someone use-block will be responsible for disposing the value
    if (victim.evicted.compareAndSet(expectedValue = false, newValue = true) && victim.refCount.load() == 0) {
      finalizeEviction(victim)
    }
  }

  private fun evictFromProbationary() {
    val victim = probationaryCache.removeEldest() ?: return
    // if refCount is not zero, use-block will be responsible for disposing the value
    if (victim.evicted.compareAndSet(expectedValue = false, newValue = true) && victim.refCount.load() == 0) {
      finalizeEviction(victim)
    }
  }

  private fun finalizeEviction(entry: Entry<K, V>) {
    try {
      disposeFn(entry.value)
    }
    catch (_: Throwable) {
      // todo: logging
    }
  }

  override fun contains(key: K): Boolean = lock.withLock {
    protectedCache.contains(key) || probationaryCache.contains(key)
  }

  override fun cleanup(ttl: Duration) {
    val toEvict = lock.withLock {
      buildList {
        protectedCache.allEntriesCopy().forEach { entry ->
          if (entry.refCount.load() == 0 && entry.lastAccessed.load().elapsedNow() > ttl) {
            protectedCache.remove(entry.key)?.let {
              // if another thread has already marked it, it's ok
              if (entry.evicted.compareAndSet(expectedValue = false, newValue = true)) {
                add(it)
              }
            }
          }
        }
        probationaryCache.allEntriesCopy().forEach { entry ->
          if (entry.refCount.load() == 0 && entry.lastAccessed.load().elapsedNow() > ttl) {
            probationaryCache.remove(entry.key)?.let {
              // if another thread has already marked it, it's ok
              if (entry.evicted.compareAndSet(expectedValue = false, newValue = true)) {
                add(it)
              }
            }
          }
        }
      }
    }
    toEvict.forEach { entry ->
      finalizeEviction(entry)
    }
  }

  override fun clear() {
    lock.withLock {
      val allEntries = probationaryCache.allEntriesCopy() + protectedCache.allEntriesCopy()
      for (entry in allEntries) {
        if (entry.evicted.compareAndSet(expectedValue = false, newValue = true) && entry.refCount.load() == 0) {
          disposeFn(entry.value)
        }
      }
      probationaryCache.clear()
      protectedCache.clear()
    }
  }

  override fun close() {
    clear()
  }

  override fun size(): Int = lock.withLock {
    probationaryCache.size() + protectedCache.size()
  }

  /**
   * Least Recently Used cache segment implementation.
   *
   * Uses a combination of:
   * - HashMap for O(1) key lookup
   * - Double-linked list for O(1) LRU ordering
   *
   * ## Invariants
   * - `head` points to most recently used entry (or null if empty)
   * - `tail` points to least recently used entry (or null if empty)
   * - All entries in a map are also in the linked list
   * - All entries in a linked list are also in the map
   * - For single entry: head == tail
   * - For empty cache: head == null && tail == null
   */
  private class LRUSegment<K, V>() {
    private val map = mutableMapOf<K, Entry<K, V>>()

    private var head: Entry<K, V>? = null
    private var tail: Entry<K, V>? = null


    /**
     * Gets an entry and moves it to the head (most recently used position).
     *
     * @param key The key to look up
     * @return The entry if found, null otherwise
     */
    fun get(key: K): Entry<K, V>? {
      return map[key]?.also { moveToHead(it) }
    }

    /**
     * Adds or updates an entry, placing it at the head.
     * If the key already exists, the old entry is removed first.
     *
     * @param key The key
     * @param entry The entry to add
     */
    fun put(key: K, entry: Entry<K, V>) {
      map[key]?.let { removeEntry(it) }
      map[key] = entry
      addToHead(entry)
    }

    /**
     * Checks if a key exists without affecting LRU order.
     */
    fun contains(key: K): Boolean {
      return map.containsKey(key)
    }

    /**
     * Removes and returns an entry by key.
     *
     * @param key The key to remove
     * @return The removed entry, or null if not found
     */
    fun remove(key: K): Entry<K, V>? {
      return map.remove(key)?.also { removeEntry(it) }
    }

    /**
     * Removes and returns the least recently used (eldest) entry.
     *
     * @return The eldest entry, or null if the cache is empty
     */
    fun removeEldest(): Entry<K, V>? {
      val last = tail
      return if (last != null) {
        map.remove(last.key)
        removeEntry(last)
        last
      }
      else null
    }

    /**
     * Moves an entry to the head (most recently used position).
     * This is the core LRU operation that maintains access order.
     *
     * @param entry The entry to move (must be in this segment)
     */
    fun moveToHead(entry: Entry<K, V>) {
      if (head == entry) return

      // Remove from the current position
      entry.prev?.next = entry.next
      entry.next?.prev = entry.prev

      // Update tail if we're moving the tail entry
      if (tail == entry) {
        tail = entry.prev
      }

      addToHead(entry)
    }

    /**
     * Returns the current number of entries.
     */
    fun size(): Int = map.size

    /**
     * Removes all entries and resets the data structure.
     */
    fun clear() {
      map.clear()
      head = null
      tail = null
    }

    /**
     * Returns a snapshot of all entries for iteration.
     * The returned list is safe to iterate without holding locks.
     */
    fun allEntriesCopy(): List<Entry<K, V>> {
      return map.values.toList()
    }

    /**
     * Adds an entry at the head (most recently used position).
     *
     * @param entry The entry to add (must not already be in the list)
     */
    private fun addToHead(entry: Entry<K, V>) {
      entry.prev = null
      entry.next = head
      head?.prev = entry
      head = entry

      if (tail == null) {
        tail = entry
      }
    }

    /**
     * Removes an entry from the linked list structure.
     * Updates head and tail pointers as needed.
     *
     * @param entry The entry to remove (must be in the list)
     */
    private fun removeEntry(entry: Entry<K, V>) {
      entry.prev?.next = entry.next
      entry.next?.prev = entry.prev
      if (head == entry) head = entry.next
      if (tail == entry) tail = entry.prev
      entry.prev = null
      entry.next = null
    }
  }
}
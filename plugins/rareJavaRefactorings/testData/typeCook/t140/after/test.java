interface Iterator<E> {
    E next();
}

interface Collection<E> {
    Iterator<E> iterator();
}

interface Map<K,V> {
    V put(K key, V value);
    Collection<V> values();
}

class Test {
	public void doTest () {
		Map map;
		map.put ("key", new Integer (0));

        Integer res = (Integer) map.values ().iterator ().next ();
	}
}

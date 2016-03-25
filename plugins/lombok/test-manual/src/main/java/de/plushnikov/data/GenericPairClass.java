package de.plushnikov.data;

import lombok.Data;

@Data
public class GenericPairClass<K, V> {
    private K key;
    private V value;

    public static void main(String[] args) {
        GenericPairClass<String, Integer> pairClass = new GenericPairClass<String, Integer>();
        pairClass.setKey("Hallo");
        pairClass.getKey();
        pairClass.setKey("111");
    }
}

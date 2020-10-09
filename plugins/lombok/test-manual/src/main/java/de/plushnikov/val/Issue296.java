package de.plushnikov.val;

public class Issue296 {
    public static void main(String[] args) {
        lombok.val ref = new java.util.concurrent.atomic.AtomicReference<>("");
        System.out.println(ref.get().length());
    }
}

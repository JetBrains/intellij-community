package de.plushnikov.refactoringbug;

public class Main {
    public static void main(String[] args) {
        PlayerWrapper wrapper = new PlayerWrapper();
        wrapper.getMyPLAYER(); // works
    }
}

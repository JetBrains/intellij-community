package de.plushnikov.sneakythrows;

import lombok.SneakyThrows;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public class SneakyTest {
//    @SneakyThrows(FileNotFoundException.class)
//    @SneakyThrows(IOException.class)
    @SneakyThrows
    public void readFile() {
        FileReader test = new FileReader("test");
        
        test.read();
    }

    @SneakyThrows
    public int read(FileReader in){
        return in.read();
    }
}

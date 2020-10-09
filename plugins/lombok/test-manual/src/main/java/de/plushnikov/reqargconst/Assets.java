package de.plushnikov.reqargconst;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.io.FileNotFoundException;

import static lombok.AccessLevel.PRIVATE;

@RequiredArgsConstructor(staticName = "of", access = PRIVATE)
public final class Assets {

    private final String pathName;

    public static Assets find(@NonNull String fileName) {
        try {
            return Assets.of(
                    Assets.class.getClassLoader().getResource(fileName).getPath()
            );
        } catch (NullPointerException ex) {
            throw new RuntimeException(new FileNotFoundException("\"" + fileName + "\""));
        }
    }

  public static void main(String[] args) {
    Assets name = new Assets("name");
    Assets assets = Assets.of("");
  }
}

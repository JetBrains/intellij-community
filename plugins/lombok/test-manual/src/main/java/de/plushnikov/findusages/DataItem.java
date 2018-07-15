package de.plushnikov.findusages;

import lombok.*;
import lombok.experimental.Accessors;
import sun.security.util.Password;

import java.time.Instant;

@Value
@ToString
@Accessors(fluent = true)
@Builder
public class DataItem {
  @NonNull
  String name;
  String anotherName;
  @NonNull
  Instant createTimestamp;
  @NonNull
  String content;

  @Getter
  @Accessors(fluent = true)
  final Password password;

  public static void main(String[] args) {
    DataItem item = DataItem.builder()
      .name("a")
      .anotherName("a")
      .createTimestamp(Instant.now())
      .content("content")
      .build();
    System.out.println("Item.name = " + item.name());
    System.out.println("Item.name = " + item.anotherName());
    System.out.println(item.password());
  }
}

package de.plushnikov.wither;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Wither;

@AllArgsConstructor
@Data
public class WitherWithData {
    private String ssn;
    private String name;

    @Wither
    private String phone;

    public static void main(String[] args) {
        WitherWithData withData = new WitherWithData("a", "b", "c");
        withData.getPhone();
        System.out.println(withData.withPhone("1234"));
    }
}

package de.plushnikov.onannotation;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.validation.constraints.Max;

@AllArgsConstructor(onConstructor=@__(@Deprecated))
public class Test {
    @Getter(onMethod=@_({@Id, @Column(name="unique-id")}))
    @Setter(onParam=@_(@Max(10000)))
    private long unid;

    public static void main(String[] args) {
        Test test = new Test(1L);
        System.out.println(test);
    }
}

package test;

import java.util.Comparator;
import java.util.List;

public class LocationSorter {
    void sort(List<Location> locations) {
        locations.sort(Comparator.comparing(Location::getUnLocode));
        for (Location location : locations) {
            System.out.println(location.getName());
        }
    }
}

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter(AccessLevel.PACKAGE)
public class EncapsulateLombokFields {
  // Fields I want to replace with getter, in this class-code
  private String distanceFunction;
  @Setter
  private double maxDistanceFunction;
  private int qualityFunction;
  private Date uwbScoreFilter;

  // Fields I want to keep unaltered
  private long obstructionTime;
  private boolean guessed;
  private double timeInterval;
  private String beyondWalls;

  public EncapsulateLombokFields() {
    this.setUp();
  }

  public void setUp() {
    // Field instantiates here
    distanceFunction = "xxx";
    maxDistanceFunction = 1.1;
    qualityFunction = 100;
    uwbScoreFilter = new Date();

    obstructionTime = 0;
    guessed = true;
    timeInterval = 2.2;
    beyondWalls = "yyy";
  }

  public boolean applyPostHeuristics() {
    // Main code of this class where fields are used without getter
    if (!distanceFunction.isEmpty()) {
      maxDistanceFunction *= 10;
      guessed = false;
    }
    if (qualityFunction < 100) {
      beyondWalls = uwbScoreFilter.toString();
    }
    return true;
  }
}
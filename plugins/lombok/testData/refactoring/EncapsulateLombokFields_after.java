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
    setDistanceFunction("xxx");
    setMaxDistanceFunction(1.1);
    setQualityFunction(100);
    setUwbScoreFilter(new Date());

    obstructionTime = 0;
    guessed = true;
    timeInterval = 2.2;
    beyondWalls = "yyy";
  }

  public boolean applyPostHeuristics() {
    // Main code of this class where fields are used without getter
    if (!getDistanceFunction().isEmpty()) {
      setMaxDistanceFunction(getMaxDistanceFunction() * 10);
      guessed = false;
    }
    if (getQualityFunction() < 100) {
      beyondWalls = getUwbScoreFilter().toString();
    }
    return true;
  }

    public void setDistanceFunction(String distanceFunction) {
        this.distanceFunction = distanceFunction;
    }

    public void setQualityFunction(int qualityFunction) {
        this.qualityFunction = qualityFunction;
    }

    public void setUwbScoreFilter(Date uwbScoreFilter) {
        this.uwbScoreFilter = uwbScoreFilter;
    }
}
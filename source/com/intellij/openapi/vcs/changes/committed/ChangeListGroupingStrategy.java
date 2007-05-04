package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;

import java.util.Calendar;
import java.util.Comparator;

/**
 * @author yole
 */
public interface ChangeListGroupingStrategy {
  String getGroupName(CommittedChangeList changeList);
  Comparator<CommittedChangeList> getComparator();

  ChangeListGroupingStrategy DATE = new ChangeListGroupingStrategy() {
    public String toString() {
      return "Date";
    }

    public String getGroupName(final CommittedChangeList list) {
      Calendar curCal = Calendar.getInstance();
      Calendar clCal = Calendar.getInstance();
      clCal.setTime(list.getCommitDate());
      if (curCal.get(Calendar.YEAR) == clCal.get(Calendar.YEAR)) {
        if (curCal.get(Calendar.MONTH) == clCal.get(Calendar.MONTH)) {
          return "This Month";
        }
        return "This Year";
      }
      return "Older";
    }

    public Comparator<CommittedChangeList> getComparator() {
      return new Comparator<CommittedChangeList>() {
        public int compare(final CommittedChangeList o1, final CommittedChangeList o2) {
          return -o1.getCommitDate().compareTo(o2.getCommitDate());
        }
      };
    }
  };

  ChangeListGroupingStrategy USER = new ChangeListGroupingStrategy() {
    public String toString() {
      return "User";
    }

    public String getGroupName(final CommittedChangeList changeList) {
      return changeList.getCommitterName();
    }

    public Comparator<CommittedChangeList> getComparator() {
      return new Comparator<CommittedChangeList>() {
        public int compare(final CommittedChangeList o1, final CommittedChangeList o2) {
          int rc = o1.getCommitterName().compareToIgnoreCase(o2.getCommitterName());
          if (rc == 0) {
            return -o1.getCommitDate().compareTo(o2.getCommitDate());
          }
          return rc;
        }
      };
    }
  };
}

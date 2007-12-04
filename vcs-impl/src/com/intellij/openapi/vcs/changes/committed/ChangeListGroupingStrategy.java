package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.NonNls;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Locale;

/**
 * @author yole
 */
public interface ChangeListGroupingStrategy {
  String getGroupName(CommittedChangeList changeList);
  Comparator<CommittedChangeList> getComparator();

  ChangeListGroupingStrategy DATE = new ChangeListGroupingStrategy() {
    @NonNls private SimpleDateFormat myWeekdayFormat = new SimpleDateFormat("EEEE", Locale.ENGLISH);
    @NonNls private SimpleDateFormat myMonthFormat = new SimpleDateFormat("MMMM", Locale.ENGLISH);
    @NonNls private SimpleDateFormat myMonthYearFormat = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH);

    public String toString() {
      return VcsBundle.message("date.group.title");
    }

    public String getGroupName(final CommittedChangeList list) {
      Calendar curCal = Calendar.getInstance();
      Calendar clCal = Calendar.getInstance();
      clCal.setTime(list.getCommitDate());
      if (curCal.get(Calendar.YEAR) == clCal.get(Calendar.YEAR)) {
        if (curCal.get(Calendar.DAY_OF_YEAR) == clCal.get(Calendar.DAY_OF_YEAR)) {
          return VcsBundle.message("date.group.today");
        }
        if (curCal.get(Calendar.WEEK_OF_YEAR) == clCal.get(Calendar.WEEK_OF_YEAR)) {
          return myWeekdayFormat.format(list.getCommitDate());
        }
        if (curCal.get(Calendar.WEEK_OF_YEAR) == clCal.get(Calendar.WEEK_OF_YEAR)+1) {
          return VcsBundle.message("date.group.last.week");
        }
        return myMonthFormat.format(list.getCommitDate());
      }
      return myMonthYearFormat.format(list.getCommitDate());
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
      return VcsBundle.message("user.group.title");
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

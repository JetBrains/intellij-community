/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.diff;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.Enumerator;
import gnu.trove.TObjectHashingStrategy;

import java.util.ArrayList;

import org.jetbrains.annotations.NonNls;

/**
 * @author dyoma
 */
public class Diff {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.diff.Diff");

  public static <T> Change buildChanges(T[] objects1, T[] objects2) {
    // Old variant of enumerator worked incorrectly with null values.
    // This check is to ensure that the corrected version does not introduce bugs.
    for( int i = 0; i < objects1.length; i++ )
      LOG.assertTrue( objects1[i] != null );
    for( int i = 0; i < objects2.length; i++ )
      LOG.assertTrue( objects2[i] != null );

    Enumerator<T> enumerator = new Enumerator<T>(objects1.length + objects2.length, TObjectHashingStrategy.CANONICAL);
    int[] ints1 = enumerator.enumerate(objects1);
    int[] ints2 = enumerator.enumerate(objects2);
    Reindexer reindexer = new Reindexer();
    int[][] discarded = reindexer.discardUnique(ints1, ints2);
    IntLCS intLCS = new IntLCS(discarded[0], discarded[1]);
    intLCS.execute();
    ChangeBuilder builder = new ChangeBuilder();
    reindexer.reindex(intLCS.getPaths(), builder);
    return builder.getFirstChange();
  }

  public static class Change {
    // todo remove. Return lists instead.
    /**
     * Previous or next edit command.
     */
    public Change link;
    /** # lines of file 1 changed here.  */
    public final int inserted;
    /** # lines of file 0 changed here.  */
    public final int deleted;
    /** Line number of 1st deleted line.  */
    public final int line0;
    /** Line number of 1st inserted line.  */
    public final int line1;

    /** Cons an additional entry onto the front of an edit script OLD.
     LINE0 and LINE1 are the first affected lines in the two files (origin 0).
     DELETED is the number of lines deleted here from file 0.
     INSERTED is the number of lines inserted here in file 1.

     If DELETED is 0 then LINE0 is the number of the line before
     which the insertion was done; vice versa for INSERTED and LINE1.  */
    protected Change(int line0, int line1, int deleted, int inserted, Change old) {
      this.line0 = line0;
      this.line1 = line1;
      this.inserted = inserted;
      this.deleted = deleted;
      this.link = old;
      //System.err.println(line0+","+line1+","+inserted+","+deleted);
    }

    @NonNls
    public String toString() {
      return "change[" + "inserted=" + inserted + ", deleted=" + deleted + ", line0=" + line0 + ", line1=" + line1 + "]";
    }

    public ArrayList<Change> toList() {
      ArrayList<Change> result = new ArrayList<Change>();
      Change current = this;
      while (current != null) {
        result.add(current);
        current = current.link;
      }
      return result;
    }
  }

  public static class ChangeBuilder implements LCSBuilder {
    private int myIndex1 = 0;
    private int myIndex2 = 0;
    private Change myFirstChange;
    private Change myLastChange;

    public void addChange(int first, int second) {
      Change change = new Change(myIndex1, myIndex2, first, second, null);
      if (myLastChange != null) myLastChange.link = change;
      else myFirstChange = change;
      myLastChange = change;
      skip(first, second);
    }

    private void skip(int first, int second) {
      myIndex1 += first;
      myIndex2 += second;
    }

    public void addEqual(int length) {
      skip(length, length);
    }

    public Change getFirstChange() {
      return myFirstChange;
    }
  }
}

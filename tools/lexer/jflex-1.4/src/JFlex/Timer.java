/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * JFlex 1.4.1                                                             *
 * Copyright (C) 1998-2004  Gerwin Klein <lsf@jflex.de>                    *
 * All rights reserved.                                                    *
 *                                                                         *
 * This program is free software; you can redistribute it and/or modify    *
 * it under the terms of the GNU General Public License. See the file      *
 * COPYRIGHT for more information.                                         *
 *                                                                         *
 * This program is distributed in the hope that it will be useful,         *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of          *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the           *
 * GNU General Public License for more details.                            *
 *                                                                         *
 * You should have received a copy of the GNU General Public License along *
 * with this program; if not, write to the Free Software Foundation, Inc., *
 * 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA                 *
 *                                                                         *
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
package JFlex;

/**
 * Very simple timer for code generation time statistics.
 *
 * Not very exact, measures user time, not processor time.
 *
 * @author Gerwin Klein
 * @version JFlex 1.4.1, $Revision: 2.3 $, $Date: 2004/11/06 23:03:32 $
 */
public class Timer {

  /* the timer stores start and stop time from currentTimeMillis() */
  private long startTime, stopTime; 

  /* flag if the timer is running (if stop time is valid) */
  private boolean running; 


  /**
   * Construct a new timer that starts immediatly.
   */
  public Timer() {
    startTime = System.currentTimeMillis();
    running = true;
  }

  
  /**
   * Start the timer. If it is already running, the old start
   * time is lost.
   */
  public void start() {
    startTime = System.currentTimeMillis();
    running = true;
  }


  /**
   * Stop the timer.
   */
  public void stop() {
    stopTime = System.currentTimeMillis();
    running = false;
  }

  
  /**
   * Return the number of milliseconds the timer has been running.
   *
   * (up till now, if it still runs, up to the stop time if it has been stopped)
   */
  public long diff() {
    if (running) 
      return System.currentTimeMillis()-startTime;
    else 
      return stopTime-startTime;    
  }

  
  /**
   * Return a string representation of the timer.
   *
   * @return a string displaying the diff-time in readable format (h m s ms)
   *
   * @see Timer#diff
   */
  public String toString() {
    long diff = diff();
    
    long millis = diff%1000;
    long secs = (diff/1000)%60;
    long mins = (diff/(1000*60))%60;
    long hs = (diff/(1000*3600))%24;
    long days = diff/(1000*3600*24);

    if (days > 0) 
      return days+"d "+hs+"h "+mins+"m "+secs+"s "+millis+"ms";

    if (hs > 0)
      return hs+"h "+mins+"m "+secs+"s "+millis+"ms";

    if (mins > 0)
      return mins+"m "+secs+"s "+millis+"ms";

    if (secs > 0)
      return secs+"s "+millis+"ms";

    return millis+"ms";
  }
}

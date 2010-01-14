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

package JFlex.gui;

import JFlex.*;

import java.io.File;


/**
 * Low priority thread for code generation (low priority 
 * that gui has time for screen updates)
 *
 * @author Gerwin Klein
 * @version JFlex 1.4.1, $Revision: 2.6 $, $Date: 2004/11/06 23:03:33 $
 */
public class GeneratorThread extends Thread {

  /** there must be at most one instance of this Thread running */
	private static volatile boolean running = false;

	/** input file setting from GUI */
	String  inputFile;

  /** output directory */
  String outputDir;
  
  /** main UI component, likes to be notified when generator finishes */
  MainFrame parent;

	/**
	 * Create a new GeneratorThread, but do not run it yet.
	 * 
	 * @param parent      the frame, main UI component
	 * @param inputFile   input file from UI settings
	 * @param messages    where generator messages should appear
	 * @param outputDir   output directory from UI settings
	 */
  public GeneratorThread(MainFrame parent, String inputFile, 
                         String outputDir) {
    this.parent    = parent;
    this.inputFile = inputFile;
    this.outputDir = outputDir;
  }


	/**
	 * Run the generator thread. Only one instance of it can run at any time.
	 */
  public void run() {
  	if (running) {
  		Out.error(ErrorMessages.ALREADY_RUNNING);
			parent.generationFinished(false);
  	}
  	else {
  		running = true;
			setPriority(MIN_PRIORITY);    
			try {
        if (!outputDir.equals("")) {
          Options.setDir(outputDir);
        }
				Main.generate(new File(inputFile));
				Out.statistics();
				parent.generationFinished(true);
			}
			catch (GeneratorException e) {
				Out.statistics();
				parent.generationFinished(false);
			}
      finally {
        running = false;
      }
  	}
  }
}

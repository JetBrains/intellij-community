/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * JFlex Anttask                                                           *
 * Copyright (C) 2001       Rafal Mantiuk <Rafal.Mantiuk@bellstream.pl>    *
 * Copyright (C) 2003       changes by Gerwin Klein <lsf@jflex.de>         *
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

package JFlex.anttask;

import JFlex.Main;
import JFlex.Options;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;

/**
 * JFlex task class
 *
 * @author Rafal Mantiuk
 * @version JFlex 1.4.1, $Revision: 2.14 $, $Date: 2004/11/06 23:03:33 $
 */
public class JFlexTask extends Task {
  private File inputFile;

	// found out by looking into .flex file 
	private String className = null;
	private String packageName = null;

  /** for javac-like dest dir behaviour */
  private File destinationDir;
	
	/** the actual output directory (outputDir = destinationDir + package)) */
	private File outputDir = null;

  public JFlexTask() {
    // ant default is different from the rest of JFlex
    setVerbose(false);
    Options.progress = false;
  }

  public void execute() throws BuildException {
   	try {
      if (inputFile == null) 
        throw new BuildException("Input file needed. Use <jflex file=\"your_scanner.flex\"/>");

			if (!inputFile.canRead()) 
				throw new BuildException("Cannot read input file "+inputFile);

			try {
      	findPackageAndClass();        
        normalizeOutdir();
        File destFile = new File(outputDir, className + ".java");
        
        if (inputFile.lastModified() > destFile.lastModified()) {      
          Main.generate(inputFile);      
          if (!Options.verbose)
            System.out.println("Generated: " + destFile.getName());
        }
      } catch (IOException e1) {
        throw new BuildException("IOException: " + e1.toString());
      }
    } catch (JFlex.GeneratorException e) {
      throw new BuildException("JFlex: generation failed!");
    }
  }

	/**
	 * Peek into .flex file to get package and class name
	 * 
	 * @throws IOException  if there is a problem reading the .flex file 
	 */
	public void findPackageAndClass() throws IOException {
		// find name of the package and class in jflex source file
		packageName = null;
		className = null;

		LineNumberReader reader = new LineNumberReader(new FileReader(inputFile));

		while (className == null || packageName == null) {
			String line = reader.readLine();
			if (line == null)	break;

			if (packageName == null) {
				int index = line.indexOf("package");
				if (index >= 0) {
					index += 7;

					int end = line.indexOf(';', index);
					if (end >= index) {
						packageName = line.substring(index, end);
						packageName = packageName.trim();
					}
				}
			}

			if (className == null) {
				int index = line.indexOf("%class");
				if (index >= 0) {
					index += 6;

					className = line.substring(index);
					className = className.trim();
				}
			}
		}

		// package name may be null, but class name not
		if (className == null) className = "Yylex";
	}

	/**
	 * Sets the actual output directory if not already set. 	
	 *
	 * Uses javac logic to determine output dir = dest dir + package name
	 * If not destdir has been set, output dir = parent of input file
	 * 
	 * Assumes that package name is already set. 
	 */
  public void normalizeOutdir() {
  	if (outputDir != null) return;
  	
    // find out what the destination directory is. Append packageName to dest dir.      
    File destDir;
    
    // this is not the default the jflex logic, but javac-like 
    if (destinationDir != null) {
      if (packageName == null) {
    		destDir = destinationDir;
      }
      else {
        String path = packageName.replace('.', File.separatorChar);
        destDir = new File(destinationDir,path);
      }
    } else { //save parser to the same dir as .flex
      destDir = new File(inputFile.getParent());
    }
    
    setOutdir(destDir);
  }

	/**
	 * @return package name of input file
	 * 
	 * @see JFlexTask.findPackageAndClass
	 */
	public String getPackage() {
		return packageName;
	}

	/**
	 * @return class name of input file
	 * 
	 * @see JFlexTask.findPackageAndClass
	 */
	public String getClassName() {
		return className;
	}

  public void setDestdir(File destinationDir) {
    this.destinationDir = destinationDir;
  }

	public void setOutdir(File outDir) {
		this.outputDir = outDir;
    Options.setDir(outputDir);
	}

  public void setFile(File file) {
    this.inputFile = file;
  }

  public void setGenerateDot(boolean genDot) {
    setDot(genDot);
  }

  public void setTimeStatistics(boolean displayTime) {
    Options.time = displayTime;
  }
  
  public void setTime(boolean displayTime) {
    setTimeStatistics(displayTime);
  }

  public void setVerbose(boolean verbose) {
    Options.verbose = verbose;
  }

  public void setSkeleton(File skeleton) {
    Options.setSkeleton(skeleton);
  }
 
  public void setSkel(File skeleton) {
    setSkeleton(skeleton);
  }

  public void setSkipMinimization(boolean skipMin) {
    setNomin(skipMin);
  }
  
  public void setNomin(boolean b) {
  	Options.no_minimize = b;
  }

  public void setNobak(boolean b) {
    Options.no_backup = b;
  }

  public void setSwitch(boolean b) {
    if (b) {
      Options.gen_method = Options.SWITCH;
    }
    else {
      Options.gen_method = Options.PACK;
    }
  }

  public void setTable(boolean b) {
    if (b) {
      Options.gen_method = Options.TABLE;
    }
    else {
      Options.gen_method = Options.PACK;
    }
  }

  public void setPack(boolean b) {
    if (b) {
      Options.gen_method = Options.PACK;
    }
    else {
      Options.gen_method = Options.SWITCH;
    }    
  }

  public void setDot(boolean b) {
    Options.dot = b;
  }

  public void setDump(boolean b) {
    Options.dump = b;
  }
  
  public void setJLex(boolean b) {    
    Options.jlex = b;
  }

  public void setCharAt(boolean b) {
    Options.char_at = b;
  }
}

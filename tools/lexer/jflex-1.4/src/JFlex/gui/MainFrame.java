/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * JFlex 1.4.3                                                             *
 * Copyright (C) 1998-2009  Gerwin Klein <lsf@jflex.de>                    *
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

import JFlex.Main;
import JFlex.Out;

import java.io.File;
import java.awt.*;
import java.awt.event.*;

/**
 * JFlex main application frame (GUI mode only)
 *
 * @author Gerwin Klein
 * @version $Revision: 1.4.3 $, $Date: 2009/12/21 15:58:48 $
 */
final public class MainFrame extends Frame implements Handles {

  private volatile boolean choosing;

  private String fileName = "";
  private String dirName = "";
  
  private Button quit; 
  private Button options;
  private Button generate;
  private Button stop;
  private Button specChoose; 
  private Button dirChoose;

  private TextField spec;
  private TextField dir;

  private TextArea messages;

  private GeneratorThread thread;

  private OptionsDialog dialog;

  
  public MainFrame() {
    super("JFlex "+Main.version);
    buildContent();
    
    addWindowListener( new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        quit();
      }
    });
    
    pack();
    show();
  }


  private void buildContent() {
    setBackground(SystemColor.control);

    generate   = new Button("Generate");
    quit       = new Button("Quit");
    options    = new Button("Options");
    stop       = new Button("Stop");
    dirChoose  = new Button("Browse");
    dir        = new TextField(10);
    specChoose = new Button("Browse");
    spec       = new TextField(10);            
    messages   = new TextArea(10,80);

    messages.setEditable(false);
    Font font = messages.getFont();
    if (font != null)
      messages.setFont(new Font("Monospaced", font.getStyle(), font.getSize()));
    else
      messages.setFont(new Font("Monospaced", Font.PLAIN, 12));

    Out.setGUIMode(messages);

    generate.addActionListener( new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        generate();
      }
    } );

    options.addActionListener( new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        showOptions();
      }
    } );

    quit.addActionListener( new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        quit();
      }
    } );
    
    stop.addActionListener( new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        stop();
      }
    } );        

    specChoose.addActionListener( new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        specChoose();
      }
    } );

    dirChoose.addActionListener( new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        dirChoose();
      }
    } );

    spec.addActionListener( new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        fileName = spec.getText();
        generate();
      }
    } );
    
    spec.addTextListener( new TextListener() {
      public void textValueChanged(TextEvent e) {
        fileName = spec.getText();
      }
    } );
    
    dir.addActionListener( new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        dirName = dir.getText();
        generate();
      }
    } );
    
    dir.addTextListener( new TextListener() {
      public void textValueChanged(TextEvent e) {
        dirName = dir.getText();
      }
    } );

    GridPanel north = new GridPanel(5,4,10,10);
    north.setInsets( new Insets(10,5,5,10) );

    north.add( 4,0, quit);
    north.add( 4,1, generate);
    north.add( 4,2, options);
    north.add( 4,3, stop);

    north.add( 0,0, BOTTOM, new Label("Lexical specification:"));
    north.add( 0,1, 2,1, spec);
    north.add( 2,1, specChoose);

    north.add( 0,2, BOTTOM, new Label("Output directory:"));
    north.add( 0,3, 2,1, dir);
    north.add( 2,3, dirChoose);

    Panel center = new Panel(new BorderLayout());   
    center.add("North", new Label("Messages:"));
    center.add("Center", messages);

    add("North", north);
    add("Center", center);

    setEnabledAll(false);
  }

  protected void showOptions() {
    if (dialog == null) {
      dialog = new OptionsDialog(this);
    }
    dialog.show();
  }


  public Dimension getPreferredSize() {
    Dimension d = super.getPreferredSize();
    d.width = messages.getPreferredSize().width;
    return d;
  }
  
  private void setEnabledAll(boolean generating) {
    stop.setEnabled( generating );
    quit.setEnabled( !generating );
    generate.setEnabled( !generating );
    dirChoose.setEnabled( !generating );
    dir.setEnabled( !generating );
    specChoose.setEnabled( !generating );
    spec.setEnabled( !generating );
  }

  private void generate() {
    // workaround for a weird AWT bug
    if (choosing) return;
   
    setEnabledAll(true);

    thread = new GeneratorThread(this, fileName, dirName);
    thread.start();
  } 

  public void generationFinished(boolean success) {
    setEnabledAll(false);
    
    if (success) 
      messages.append(Out.NL+"Generation finished successfully."+Out.NL);
    else
      messages.append(Out.NL+"Generation aborted."+Out.NL);
  }

  private void stop() {
    if (thread != null) {
    	/* stop ok here despite deprecation (?)
    	   I don't know any good way to abort generation without changing the
     		 generator code */ 
      thread.stop();
      thread = null;
    }
    generationFinished(false);
  }
 
  private void quit() {
    setVisible(false);
    System.exit(0);
  }
  
  private void dirChoose() {
    choosing = true;
    
    FileDialog d = new FileDialog(this, "Choose directory", FileDialog.LOAD);
    
    d.show();
    
    if (d.getDirectory() != null) {
      dir.setText( (new File(d.getDirectory())).getAbsolutePath() );
    }
    
    choosing = false;    
  }

  private void specChoose() {
    choosing = true;
    
    FileDialog d = new FileDialog(this, "Choose file", FileDialog.LOAD);
    
    d.setFile("*.flex");
    d.show();
    
    if (d.getFile() != null) {
      fileName = d.getDirectory()+d.getFile();
      dir.setText(d.getDirectory());
      spec.setText(fileName);
    }
    
    choosing = false;    
  }
    
}

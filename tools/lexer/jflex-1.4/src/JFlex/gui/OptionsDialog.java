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

import java.awt.*;
import java.awt.event.*;

import java.io.File;

import JFlex.GeneratorException;
import JFlex.Options;
import JFlex.Skeleton;

/**
 * A dialog for setting JFlex options
 * 
 * @author Gerwin Klein
 * @version $Revision: 1.4.3 $, $Date: 2009/12/21 15:58:48 $
 */
public class OptionsDialog extends Dialog {

  private Frame owner;

  private Button skelBrowse;
  private TextField skelFile;

  private Button ok;
  private Button defaults;

  private Checkbox dump;
  private Checkbox verbose;
  private Checkbox jlex;
  private Checkbox no_minimize; 
  private Checkbox no_backup; 
  private Checkbox time;
  private Checkbox dot;

  private Checkbox tableG;
  private Checkbox switchG;
  private Checkbox packG; 
  

  /**
   * Create a new options dialog
   * 
   * @param owner
   */
  public OptionsDialog(Frame owner) {
    super(owner, "Options");

    this.owner = owner;
    
    setup();
    pack();
    
    addWindowListener( new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        close();
      }
    });
  }

  public void setup() {
    // create components
    ok = new Button("Ok");
    defaults = new Button("Defaults");    
    skelBrowse = new Button(" Browse");
    skelFile = new TextField();
    skelFile.setEditable(false);
    dump = new Checkbox(" dump");
    verbose = new Checkbox(" verbose");

    jlex = new Checkbox(" JLex compatibility");
    no_minimize = new Checkbox(" skip minimization");
    no_backup = new Checkbox(" no backup file");
    time = new Checkbox(" time statistics");
    dot = new Checkbox(" dot graph files");

    CheckboxGroup codeG = new CheckboxGroup();
    tableG = new Checkbox(" table",Options.gen_method == Options.TABLE, codeG);
    switchG = new Checkbox(" switch",Options.gen_method == Options.SWITCH, codeG);
    packG = new Checkbox(" pack",Options.gen_method == Options.PACK, codeG);
    
    // setup interaction
    ok.addActionListener( new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        close();
      }
    } );

    defaults.addActionListener( new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        setDefaults();
      }
    } );

    skelBrowse.addActionListener( new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        skelBrowse();
      }
    } );

    tableG.addItemListener( new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        setGenMethod();
      }
    } );

    verbose.addItemListener( new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        Options.verbose = verbose.getState();                    
      }
    } );

    dump.addItemListener( new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        Options.dump = dump.getState();                    
      }
    } );

    jlex.addItemListener( new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        Options.jlex = jlex.getState();                    
      }
    } );

    no_minimize.addItemListener( new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        Options.no_minimize = no_minimize.getState();                    
      }
    } );
    
    no_backup.addItemListener( new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        Options.no_backup = no_backup.getState();                    
      }
    } );

    dot.addItemListener( new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        Options.dot = dot.getState();                    
      }
    } );

    time.addItemListener( new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        Options.time = time.getState();                    
      }
    } );

    // setup layout
    GridPanel panel = new GridPanel(4,7,10,10);
    panel.setInsets( new Insets(10,5,5,10) );
    
    panel.add(3,0,ok);
    panel.add(3,1,defaults);
     
    panel.add(0,0,2,1,Handles.BOTTOM,new Label("skeleton file:"));
    panel.add(0,1,2,1,skelFile);
    panel.add(2,1,1,1,Handles.TOP, skelBrowse);
     
    panel.add(0,2,1,1,Handles.BOTTOM,new Label("code:"));
    panel.add(0,3,1,1,tableG);
    panel.add(0,4,1,1,switchG);
    panel.add(0,5,1,1,packG);

    panel.add(1,3,1,1,dump);
    panel.add(1,4,1,1,verbose);
    panel.add(1,5,1,1,time);
    

    panel.add(2,3,1,1,no_minimize);
    panel.add(2,4,1,1,no_backup);

    panel.add(3,3,1,1,jlex);
    panel.add(3,4,1,1,dot);
         
    add("Center",panel);
    
    updateState();
  }
  
  private void skelBrowse() {
    FileDialog d = new FileDialog(owner , "Choose file", FileDialog.LOAD);
    d.show();
    
    if (d.getFile() != null) {
      File skel = new File(d.getDirectory()+d.getFile());
      try {
        Skeleton.readSkelFile(skel);
        skelFile.setText(skel.toString());
      }
      catch (GeneratorException e) {
        // do nothing
      }
    }
  }

  private void setGenMethod() {
    if ( tableG.getState() ) {
      Options.gen_method = Options.TABLE;
      return;
    }
    
    if ( switchG.getState() ) {
      Options.gen_method = Options.SWITCH;
      return;
    }
    
    if ( packG.getState() ) {
      Options.gen_method = Options.PACK;
      return;
    }
  }

  private void updateState() {
    dump.setState(Options.dump);
    verbose.setState(Options.verbose);
    jlex.setState(Options.jlex);
    no_minimize.setState(Options.no_minimize); 
    no_backup.setState(Options.no_backup);
    time.setState(Options.time);
    dot.setState(Options.dot);

    tableG.setState(Options.gen_method == Options.TABLE);
    switchG.setState(Options.gen_method == Options.SWITCH);
    packG.setState(Options.gen_method == Options.PACK);     
  }

  private void setDefaults() {
    Options.setDefaults();
    Skeleton.readDefault();
    skelFile.setText("");
    updateState();
  }

  public void close() {
    hide();
  }

}

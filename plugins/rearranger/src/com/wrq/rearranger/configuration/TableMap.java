/*
 * Copyright (c) 2003, 2010, Dave Kriewall
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1) Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.wrq.rearranger.configuration;

/**
 * In a chain of data manipulators some behaviour is common. TableMap
 * provides most of this behaviour and can be subclassed by filters
 * that only need to override a handful of specific methods. TableMap
 * implements TableModel by routing all requests to its model, and
 * TableModelListener by routing all events to its listeners. Inserting
 * a TableMap which has not been subclassed into a chain of table filters
 * should have no effect.
 *
 * @version 1.4 12/17/97
 * @author Philip Milne
 */

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

public class TableMap
  extends AbstractTableModel
  implements TableModelListener
{
// ------------------------------ FIELDS ------------------------------

  protected TableModel model;

// --------------------- GETTER / SETTER METHODS ---------------------

  public void setModel(TableModel model) {
    this.model = model;
    model.addTableModelListener(this);
  }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface TableModel ---------------------


  public int getColumnCount() {
    return (model == null) ? 0 : model.getColumnCount();
  }

  public int getRowCount() {
    return (model == null) ? 0 : model.getRowCount();
  }

  public boolean isCellEditable(int row, int column) {
    return model.isCellEditable(row, column);
  }

  public Class getColumnClass(int aColumn) {
    return model.getColumnClass(aColumn);
  }

  // By default, implement TableModel by forwarding all messages
  // to the model.

  public Object getValueAt(int aRow, int aColumn) {
    return model.getValueAt(aRow, aColumn);
  }

  public void setValueAt(Object aValue, int aRow, int aColumn) {
    model.setValueAt(aValue, aRow, aColumn);
  }

  public String getColumnName(int aColumn) {
    return model.getColumnName(aColumn);
  }

// --------------------- Interface TableModelListener ---------------------

  //
// Implementation of the TableModelListener interface,
//
  // By default forward all events to all the listeners.
  public void tableChanged(TableModelEvent e) {
    fireTableChanged(e);
  }
}

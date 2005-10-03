package com.intellij.ide.todo.configurable;

import com.intellij.ide.todo.TodoFilter;
import com.intellij.ide.IdeBundle;
import com.intellij.psi.search.TodoPattern;
import com.intellij.util.ui.ItemRemovable;

import javax.swing.table.AbstractTableModel;
import java.util.Iterator;
import java.util.List;

final class FiltersTableModel extends AbstractTableModel implements ItemRemovable{
  private final String[] ourColumnNames=new String[] {
    IdeBundle.message("column.todo.filters.name"),
    IdeBundle.message("column.todo.filter.patterns")
  };
  private final Class[] ourColumnClasses=new Class[]{String.class,String.class};

  private List<TodoFilter> myFilters;

  public FiltersTableModel(List<TodoFilter> filters){
    myFilters=filters;
  }

  public String getColumnName(int column){
    return ourColumnNames[column];
  }

  public Class getColumnClass(int column){
    return ourColumnClasses[column];
  }

  public int getColumnCount(){
    return 2;
  }

  public int getRowCount(){
    return myFilters.size();
  }

  public Object getValueAt(int row,int column){
    TodoFilter filter=myFilters.get(row);
    switch(column){
      case 0:{ // "Name" column
        return filter.getName();
      }case 1:{
        StringBuffer sb=new StringBuffer();
        for(Iterator i=filter.iterator();i.hasNext();){
          TodoPattern pattern=(TodoPattern)i.next();
          sb.append(pattern.getPatternString());
          if(i.hasNext()){
            sb.append(" | ");
          }
        }
        return sb.toString();
      }default:{
        throw new IllegalArgumentException();
      }
    }
  }

  public void removeRow(int index){
    myFilters.remove(index);
    fireTableRowsDeleted(index,index);
  }
}
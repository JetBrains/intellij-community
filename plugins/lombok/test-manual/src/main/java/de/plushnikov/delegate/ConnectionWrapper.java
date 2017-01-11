package de.plushnikov.delegate;

import lombok.experimental.Delegate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ConnectionWrapper implements Connection {
  private interface Int {
    PreparedStatement prepareStatement(String s) throws SQLException;
  }

  @Delegate(excludes = Int.class)
  private Connection conn;

  @Override
  public PreparedStatement prepareStatement(String s) throws SQLException {
    return conn.prepareStatement(s);
  }

}

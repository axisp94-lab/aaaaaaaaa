package net.amethystesmp.anpc.storage;

import net.amethystesmp.anpc.ANPCPlugin;
import java.io.File;
import java.sql.*;
import java.util.Locale;

public final class VariableStore implements AutoCloseable {
    private final Connection connection;
    public VariableStore(ANPCPlugin plugin) throws SQLException {
        File db = new File(plugin.getDataFolder(), "variables.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
        try (Statement s=connection.createStatement()) { s.executeUpdate("CREATE TABLE IF NOT EXISTS variables (scope TEXT NOT NULL, owner TEXT NOT NULL, name TEXT NOT NULL, value TEXT, PRIMARY KEY(scope, owner, name))"); }
    }
    public synchronized void set(String scope,String owner,String name,Object value) {
        try(PreparedStatement p=connection.prepareStatement("INSERT INTO variables(scope,owner,name,value) VALUES(?,?,?,?) ON CONFLICT(scope,owner,name) DO UPDATE SET value=excluded.value")) { p.setString(1,scope);p.setString(2,owner==null?"":owner);p.setString(3,name.toLowerCase(Locale.ROOT));p.setString(4,String.valueOf(value));p.executeUpdate(); } catch(SQLException e){throw new IllegalStateException(e);}
    }
    public synchronized String get(String scope,String owner,String name,String fallback) {
        try(PreparedStatement p=connection.prepareStatement("SELECT value FROM variables WHERE scope=? AND owner=? AND name=?")){p.setString(1,scope);p.setString(2,owner==null?"":owner);p.setString(3,name.toLowerCase(Locale.ROOT));try(ResultSet r=p.executeQuery()){return r.next()?r.getString(1):fallback;}}catch(SQLException e){throw new IllegalStateException(e);}
    }
    @Override public void close(){try{connection.close();}catch(SQLException ignored){}}
}

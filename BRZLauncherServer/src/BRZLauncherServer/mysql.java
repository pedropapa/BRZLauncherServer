package BRZLauncherServer;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class mysql {
	private static String mysqlServerName 	= "198.211.116.169";
	private static String mysqlUser 		= "brazucas";
	private static String mysqlPasswd 		= "PirateBay";
	private static String mysqlDataBase		= "5786_kubitschek";
	private static String mysqlUrl			= "jdbc:mysql://" + mysqlServerName + "/" + mysqlDataBase;
	
	public static boolean mysqlOn			= false;
	
	public static Connection conn		= null;
	public static Connection Database 	= null;
	public static Statement stm;
	
	public static int QueryResult 	= -1;
	
	public mysql() {
		openConn();
	}
	
	public static Connection openConn() {
		try {
			String driverName = "com.mysql.jdbc.Driver";
			Class.forName(driverName);
			
			conn = DriverManager.getConnection(mysqlUrl, mysqlUser, mysqlPasswd);
			
			if(conn != null) {
				System.out.println("Conexão com o MySQL feita com sucesso!");
				mysqlOn = true;
			} else {
				System.out.println("Erro ao fazer a conexão com o banco de dados MySQL.");
				mysqlOn = false;
			}
			
			return conn;
		} catch (ClassNotFoundException e) {
			System.out.println("O driver de conexão com o MySQL não foi encontrado.");
			
			return null;
		} catch (SQLException e) {
			System.out.println("Não foi possível conectar-se ao banco de dados MySQL.");
			onError(e.getMessage());
			System.exit(0);
			
			return null;
		}
	}
	
	public static boolean closeConn() {
		try {
			mysql.conn.close();
			System.out.println("A conexão com o MySQL foi encerrada com sucesso.");
			
			return true;
		} catch (SQLException e) {
			System.out.println("Erro ao fechar a conexão MySQL");
			onError(e.getMessage());
			
			return false;
		}
	}
	
	public boolean dbConn(Connection DB) {
		try {
			Database = DB;
			stm = Database.createStatement();
			
			return true;
		} catch(SQLException e) {
			onError(e.getMessage());
			
			return false;
		}
	}
	
	public static ResultSet query(String sql, String[] values) {
		Statement st;
		String[] tipoSplit;
		String tipo;
		
		ResultSet output 	= null;
		tipoSplit 			= sql.split(" ", 2);
		tipo 				= tipoSplit[0];
		
		try {
			PreparedStatement preQuery = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
			
			if(values != null) {
				for(int x = 1; x <= values.length; ++x) {
					try {
						int n = Integer.parseInt(values[(x - 1)]);
						preQuery.setInt(x, n);
					} catch(NumberFormatException e) {
						preQuery.setString(x, values[(x - 1)]);
					}
				}
			}
			
			switch(tipo) {
				case "SELECT":
					try {
						output = preQuery.executeQuery();
					} catch (SQLException e) {
						onError(e.getMessage());
						
						return null;
					}
				break;
				case "INSERT":
				case "UPDATE":
				case "DELETE":
					QueryResult = -1;
					
					try {
						preQuery.executeUpdate();
						output = preQuery.getGeneratedKeys();
						
						QueryResult = 1;
					} catch (SQLException e) {
						onError(e.getMessage());
					}
				break;
			}
			
			
			return output;
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public static void onError(String error) {
		System.out.println("[MySQL Error] " + error);
		
		try {
			BufferedWriter out = new BufferedWriter(new FileWriter("mysql_errors.txt"));
			out.write(error + "\n");
			out.close();
		} catch (IOException e) {
			System.out.println(e.getMessage());
		}
		
		if(error.contains("No operations allowed after connection closed")) {
			openConn();
			mysqlOn = false;
		}
	}
}

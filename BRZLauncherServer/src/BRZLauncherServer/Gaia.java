package BRZLauncherServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import BRZLauncherServer.Variaveis.JogadorVars;
import BRZLauncherServer.Variaveis.PartidaVars;
import BRZLauncherServer.Variaveis.ServerVars;

public class Gaia {	
	public Mysql Dao 							= null;
	public Api Api 								= null;
	public Utils Utils 							= null;
	public Servidor Servidor 					= null;
	public ClienteComandos Cliente				= null;
	public ServidorSampComandos ServidorSamp 	= null;
	
	// Variáveis locais
	public boolean COMPETITIVO_LIBERADO 								= true;
	public ArrayList<Socket> clientOutputStreams						= null;
	public Timer timer 													= new Timer();
	private int porta													= 1961;
	public HashMap<Integer, HashMap<String, PartidaVars>> partidas		= new HashMap<Integer, HashMap<String, PartidaVars>>();
	public String servidorVersao										= "0.1a R1";
	public String DigitalOcean_clientId									= "iaCzvk978IthbxOotLr1l";
	public String DigitalOcean_apiKey									= "ZcsNAkxd3qymQz0OrDrZGncrNjtENF2bTomkDjmpw";
	public String DigitalOcean_urlApi									= "https://api.digitalocean.com/%s/?client_id="+DigitalOcean_clientId+"&api_key="+DigitalOcean_apiKey;
	
	public Utils Utils() {
		return this.Utils;
	}
	
	public void init() throws IOException {
		this.Api.atualizarMasterIP();
		go();
	}
	
	public void go() {
		Dao.query("UPDATE competitivo_contas SET LOGADO = 0", null);
		Dao.query("DELETE FROM competitivo_fila", null);
		Dao.query("DELETE FROM competitivo_servers", null);
		
		//System.out.println(Math.round((System.currentTimeMillis() / 1000) + (60 * 5)));
		
		clientOutputStreams 	= new ArrayList<Socket>();
		ServerSocket serverSock = null;
		
		try {
			serverSock = new ServerSocket(porta);
			
			System.out.println("Escutando porta "+porta+"...");
			
			while(true) {
				Socket clientSocket = serverSock.accept();
				//clientSocket.setSoTimeout(60000);
				clientOutputStreams.add(clientSocket);
				Thread t = new Thread(new ClientHandler(clientSocket));
				t.start();
				
				System.out.println("Nova conexão vinda de: "+clientSocket.getInetAddress().getHostAddress());
			}
		} catch (Exception ex) {
			if(serverSock != null) {
				try {
					serverSock.close();
				} catch (IOException e) {

				}
			}
			
			ex.printStackTrace();
		}
	}
	
	public class ClientHandler implements Runnable {
		BufferedReader 	reader 	= null;
		Socket 			sock 	= null;
		PrintWriter		writer 	= null;
		
		public ClientHandler(Socket clientSocket) {
			try {
				sock 						= clientSocket;
				InputStreamReader isReader 	= new InputStreamReader(sock.getInputStream());
				reader 						= new BufferedReader(isReader);
				writer 						= new PrintWriter(sock.getOutputStream());
				
				timer.scheduleAtFixedRate(new TimerTask() {
					@Override
					public void run() {
						try {
							formarPartidas("x1");
							formarPartidas("x3");
							formarPartidas("x5");
						} catch (SQLException | IOException e) {
							e.printStackTrace();
						}
					}
				}, 0, 15*1000);
			} catch(Exception ex) {
				ex.printStackTrace();
			}
		}
		
		public void run() {
			String resposta 			= "";
			String ip 					= sock.getInetAddress().getHostAddress();
			String nick					= null;
			int port					= 0;
			
			try {
				while((resposta = reader.readLine()) != null) {
					System.out.println("[" + Utils.dataHora() + "] Comando recebido de "+ip+": "+resposta);
					HashMap<String, String> VARS = Utils.tratar(resposta);
					String 		output			= "";
					
					if(VARS.get("t") != null && VARS.get("a") != null) {
						switch(VARS.get("t")) {
							case "server":
								// Comandos  enviados pelos Servidores SA-MP
								output = ServidorSamp.filtrar(VARS, sock);
							break;
							/** *********************************************************************************************************************************************** **/
							case "cliente":
								// Comandos enviado pelos Clientes Java
					            output = Cliente.filtrar(VARS, sock);
				            break;
						}
					}
					
					if(output != null && output.length() > 0) {
						writer.println(output);
						writer.flush();
						
						System.out.println("[" + Utils.dataHora() + "] Comando enviado para "+ip+": "+output);
					}
				}
			} catch (IOException | SQLException e) {
				//e.printStackTrace();
				
				if(Servidor.jogadoresConectados.get(nick) != null) {
					System.out.println("Conexão perdida com o cliente "+ip+" ("+nick+")");
					
					try {
						Servidor.desconectarJogador(nick);
					} catch (SQLException | IOException e1) {
						System.out.println("Não foi possível deslogar o jogador "+nick+".");
					}
				} else if(Servidor.servidoresConectados.get(ip+":"+port) != null) {
					System.out.println("Conexão perdida com o servidor "+ip+":"+port);
					Servidor.desconectarServidor(ip+":"+port);
				}
				
				try {
					sock.close();
				} catch (IOException e1) {

				}
			}
		}
	}
	
	public boolean formarPartidas(String modo) throws SQLException, IOException {
		ResultSet query, query2;
		boolean partidaFormada 	= false;
		int servidorJogando 	= 0;
		String servidorIP		= null;
		
		query2 = Dao.query("SELECT * FROM competitivo_servers WHERE STATUS = 1", new String[] {});
		
		if(query2.next()) {
			List<String> jogadores = new ArrayList<String>();
			String[] jogadoresArray = new String[Servidor.jogadoresConectados.size()];
			
			query = Dao.query("SELECT * FROM competitivo_fila WHERE MODO = ? ORDER BY TIMESTAMP DESC", new String[] {modo});
			
			Matcher r 	= (Pattern.compile("x(\\d+)")).matcher(modo);
			r.find();
			int n 		= Integer.valueOf(r.group(1)) * 2;
			
			int j = 0;
			while(query.next()) {
				JogadorVars jog = Servidor.jogadoresConectados.get(query.getString("NICK"));
				
				if(jog != null) {
					jogadoresArray[j] = jog.NICK;
					
					++j;
					
					jogadores.add(jog.NICK);
					
					if(/*j == n && */1 == 1) {
						if(partidas.get(query2.getInt("ID")) != null) {
							partidas.remove(query2.getInt("ID"));
						}
						
						partidas.put(query2.getInt("ID"), new HashMap<String, PartidaVars>());
						
						String timeA = "";
						String timeB = "";
						Collections.shuffle(jogadores);
							
						servidorJogando = query2.getInt("ID");
						servidorIP 		= query2.getString("IP")+":"+query2.getString("PORTA");
							
						Iterator<String> i = jogadores.iterator();
						int h = 0;
						while(i.hasNext()) {
							jog = Servidor.jogadoresConectados.get(i.next());

							if(++h % 2 != 0) { // Time A
								timeA += jog.NICK + "\n";
								partidas.get(query2.getInt("ID")).put(jog.NICK, new PartidaVars("TimeA"));
								jog.time = 0;
							} else { // Time B
								timeB += jog.NICK + "\n";
								partidas.get(query2.getInt("ID")).put(jog.NICK, new PartidaVars("TimeB"));
								jog.time = 1;
							}
							
							Dao.query("DELETE FROM competitivo_fila WHERE NICK = ?", new String[] {jog.NICK});
							jog.STATUS = "em_jogo";
							jog.servidorJogando = servidorJogando;
							jog.servidorIP		= servidorIP;
							
							Servidor.enviarParaCliente(jog.sock, Utils.json.toJson(Utils.tratar("funcao=partidaFormada&jogadores="+ Utils.implodeArray(jogadoresArray, ",") + "&partidaid="+servidorJogando+"&NICK="+jog.NICK)));
						}
						
						ServerVars servidorUtilizado 			= Servidor.servidoresConectados.get(query2.getString("IP")+":"+query2.getString("PORTA"));
						servidorUtilizado.STATUS 				= 2;
						servidorUtilizado.JOGADORES_CONECTADOS 	= j;
						servidorUtilizado.JOGADORES_LISTA 		= Utils.implodeArray(jogadoresArray, ",");
						
						Servidor.enviarParaServidor(servidorUtilizado.IP + ":" + servidorUtilizado.PORT, "a=TJ&LA="+timeA.replaceAll("\n", ",")+"&LB="+timeB.replaceAll("\n", ","));
						
						Dao.query("UPDATE competitivo_servers SET STATUS = 2, TIME1_PLAYERS = ?, TIME2_PLAYERS = ?, PARTIDA_TIPO = ? WHERE ID = ?", new String[] {timeA, timeB, modo, query2.getInt("ID") + ""});

						Servidor.propagarServidores();
						
						partidaFormada = true;
						
						break;
					}
				}
			}
		}
		
		return partidaFormada;
	}
}
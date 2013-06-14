package BRZLauncherServer;

import java.io.IOException;
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

import BRZLauncherServer.Utils.AESencrp;
import BRZLauncherServer.Variaveis.JogadorVars;
import BRZLauncherServer.Variaveis.PartidaVars;
import BRZLauncherServer.Variaveis.ServerVars;

public class Gaia {	
	// Referências
	public Mysql Dao 							= null;
	public Api Api 								= null;
	public Utils Utils 							= null;
	public ServidorJava Servidor 				= null;
	public ClienteComandos Cliente				= null;
	public ServidorSampComandos ServidorSamp 	= null;
	public AESencrp C 							= null;
	
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
	
	// Constantes
	public int quantidade_total_servidores = 20;
	
	public Utils Utils() {
		return this.Utils;
	}
	
	public void init() throws Exception {
		this.Api.atualizarMasterIP();
		//this.Servidor.abrirNovoServidor();
		go();
	}
	
	public void go() {
		Dao.query("UPDATE competitivo_contas SET LOGADO = 0", null);
		Dao.query("DELETE FROM competitivo_fila", null);
		Dao.query("DELETE FROM competitivo_servers", null);
		Dao.query("UPDATE competitivo_servers_oficiais SET ABERTO = 0", null);
		
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
				Thread t = new Thread(new ClienteThread(this, clientSocket));
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
	
	public class rotinaPartidas extends Gaia implements Runnable {
		private Gaia Gaia = null;
		
		public rotinaPartidas(Gaia g) {
			this.Gaia = g;
		}
		
		public void run() {
			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					try {
						formarPartidas("x1", Gaia);
						formarPartidas("x3", Gaia);
						formarPartidas("x5", Gaia);
					} catch (SQLException | IOException e) {
						e.printStackTrace();
					}
				}
			}, 10*1000);
		}
	}
	
	public boolean formarPartidas(String modo, Gaia Gaia) throws SQLException, IOException {
		System.out.println("Formando partidas "+ modo);
		
		ResultSet query, query2;
		boolean partidaFormada 	= false;
		int servidorJogando 	= 0;
		String servidorIP		= null;
		
		query2 = Gaia.Dao.query("SELECT * FROM competitivo_servers WHERE STATUS = 1", null);
		
		if(query2.next()) {
			List<String> jogadores = new ArrayList<String>();
			String[] jogadoresArray = new String[Servidor.jogadoresConectados.size()];
			
			query = Gaia.Dao.query("SELECT * FROM competitivo_fila F, competitivo_contas C WHERE F.MODO = ? AND F.NICK = C.NICK ORDER BY TIMESTAMP DESC", new String[] {modo});
			
			Matcher r 	= (Pattern.compile("x(\\d+)")).matcher(modo);
			r.find();
			int n 		= Integer.valueOf(r.group(1)) * 2;
			
			int j = 0;
			while(query.next()) {
				JogadorVars jog = Gaia.Servidor.jogadoresConectados.get(query.getString("CHAVE_AUTH"));
				
				if(jog != null) {
					jogadoresArray[j] = jog.NICK+"|"+jog.chave;
					
					++j;
					
					jogadores.add(jog.chave);
					
					if(j == n && 1 == 1) {
						if(Gaia.partidas.get(query2.getInt("ID")) != null) {
							Gaia.partidas.remove(query2.getInt("ID"));
						}
						
						Gaia.partidas.put(query2.getInt("ID"), new HashMap<String, PartidaVars>());
						
						String timeA = "";
						String timeB = "";
						Collections.shuffle(jogadores);
							
						servidorJogando = query2.getInt("ID");
						servidorIP 		= query2.getString("IP")+":"+query2.getString("PORTA");
							
						Iterator<String> i = jogadores.iterator();
						int h = 0;
						while(i.hasNext()) {
							jog = Gaia.Servidor.jogadoresConectados.get(i.next());

							if(++h % 2 != 0) { // Time A
								timeA += jog.NICK + "\n";
								Gaia.partidas.get(query2.getInt("ID")).put(jog.chave, new PartidaVars("TimeA"));
								jog.time = 0;
							} else { // Time B
								timeB += jog.NICK + "\n";
								Gaia.partidas.get(query2.getInt("ID")).put(jog.chave, new PartidaVars("TimeB"));
								jog.time = 1;
							}
							
							Gaia.Dao.query("DELETE FROM competitivo_fila WHERE NICK = ?", new String[] {jog.NICK});
							jog.STATUS = "em_jogo";
							jog.servidorJogando = servidorJogando;
							jog.servidorIP		= servidorIP;
							
							String[] jogadoresLista = new String[Servidor.jogadoresConectados.size()];
							for(String u : jogadoresArray) {
								jogadoresLista[jogadoresLista.length - 1] = u.split("\\|")[0];
							}
							
							Gaia.Servidor.enviarParaCliente(jog.sock, Gaia.Utils.json.toJson(Utils.tratar("funcao=partidaFormada&jogadores="+ Gaia.Utils.implodeArray(jogadoresLista, ",") + "&partidaid="+servidorJogando+"&NICK="+jog.NICK)));
						}
						
						ServerVars servidorUtilizado 			= Gaia.Servidor.servidoresConectados.get(query2.getString("IP")+":"+query2.getString("PORTA"));
						servidorUtilizado.STATUS 				= 2;
						servidorUtilizado.JOGADORES_CONECTADOS 	= j;
						servidorUtilizado.JOGADORES_LISTA 		= Gaia.Utils.implodeArray(jogadoresArray, ",");
						
						Gaia.Servidor.enviarParaServidor(servidorUtilizado.IP + ":" + servidorUtilizado.PORT, "a=TJ&LA="+timeA.replaceAll("\n", ",")+"&LB="+timeB.replaceAll("\n", ",")+"&PT="+(n/2));
						
						Gaia.Dao.query("UPDATE competitivo_servers SET STATUS = 2, TIME1_PLAYERS = ?, TIME2_PLAYERS = ?, PARTIDA_TIPO = ? WHERE ID = ?", new String[] {timeA, timeB, modo, query2.getInt("ID") + ""});

						Gaia.Servidor.propagarServidores();
						
						partidaFormada = true;
						
						break;
					}
				}
			}
		}
		
		return partidaFormada;
	}
}
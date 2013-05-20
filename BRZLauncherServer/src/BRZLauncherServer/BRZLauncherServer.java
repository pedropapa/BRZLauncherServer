package BRZLauncherServer;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.Date;

import javax.swing.JOptionPane;

import com.google.gson.Gson;

public class BRZLauncherServer {
	public static boolean COMPETITIVO_LIBERADO 									= true;
	public static ArrayList<Socket> clientOutputStreams							= null;
	public static mysql MySQLConn 												= null;
	public static HashMap<String, jogadorVars> jogadoresLogados 				= new HashMap<String, jogadorVars>();
	public static HashMap<String, serverVars> servidoresConectados 				= new HashMap<String, serverVars>();
	public static Gson 	json 													= new Gson();
	public static Timer timer 													= new Timer();
	private static int porta													= 1961;
	public static HashMap<Integer, HashMap<String, partidaVars>> partidas		= new HashMap<Integer, HashMap<String, partidaVars>>();
	public static String brzLauncherUrl											= "http://samp.brazucas-server.com/BRZLauncher.php";
	public static String brzUrlAPI												= "http://samp.brazucas-server.com/api.php?";
	public static String servidorVersao											= "0.1a R1";
	public static String DigitalOcean_clientId									= "iaCzvk978IthbxOotLr1l";
	public static String DigitalOcean_apiKey									= "ZcsNAkxd3qymQz0OrDrZGncrNjtENF2bTomkDjmpw";
	public static String DigitalOcean_urlApi									= "https://api.digitalocean.com/%s/?client_id="+DigitalOcean_clientId+"&api_key="+DigitalOcean_apiKey;
	public static String tempFolder												= System.getProperty("java.io.tmpdir");
	
	public static void main(String[] args) throws IOException {
		MySQLConn = new mysql();
		
		BRZLauncherServer.atualizarMasterIP();
		BRZLauncherServer.go();
	}
	
	public static class serverVars {
		public String 	IP 						= null;
		public int 		PORT 					= 0;
		
		/**
		 * Status do servidor
		 * 0 - N�o conectado
		 * 1 - Dispon�vel
		 * 2 - Em jogo
		 */
		public int 		STATUS					= 0;
		public int 		JOGADORES_CONECTADOS 	= 0;
		public Socket 	sock					= null;
		public String 	JOGADORES_LISTA			= null;
		public String 	SENHA					= null;
		public String 	chave					= null;
		
		public serverVars(String ip, int port, int status, int jogadoresConectados, Socket socket, String senha, String CHAVE) {
			IP 						= ip;
			PORT 					= port;
			STATUS				 	= status;
			JOGADORES_CONECTADOS 	= jogadoresConectados;
			sock					= socket;
			SENHA					= senha;
			chave					= CHAVE;
		}
	}
	
	public static class jogadorVars {
		public String STATUS 		= null;
		public String NICK 			= null;
		public Socket sock			= null;
		public String modo			= null;
		public boolean pronto 		= false;
		public int servidorJogando	= -1;
		public String servidorIP	= null;
		public String chave			= null;
		public int time				= -1;
		public boolean autenticado	= false;
		public int bd_punicao		= -1;
		public int bd_matou			= -1;
		public int bd_morreu		= -1;
		public int bd_jogos			= -1;
		public int bd_vitorias		= -1;
		
		/**
		 * Situa��o do jogador enquanto a partida em que joga ainda est� sendo disputada.
		 * 0 - Nenhuma situa��o (a partida n�o come�ou)
		 * 1 - Normal (est� jogando)
		 * 2 - Desconectado do servidor (saiu por qualquer raz�o do servidor enquanto a partida ainda est� sendo disputada)
		 */
		public int situacao			= 0;
		
		public jogadorVars(String nick, String status, Socket socket, String CHAVE, ResultSet competitivo_dados) {
			NICK 		= nick;
			STATUS 		= status;
			sock 		= socket;
			chave 		= CHAVE;
			
			try {
				if(competitivo_dados != null) {
					bd_punicao 	= competitivo_dados.getInt("PUNICAO");
					bd_matou	= competitivo_dados.getInt("MATOU");
					bd_morreu 	= competitivo_dados.getInt("MORREU");
					bd_jogos	= competitivo_dados.getInt("JOGOS");
					bd_vitorias = competitivo_dados.getInt("VITORIAS");
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static class partidaVars {
		public boolean pronto 	= false;
		public String time		= null;
		
		public partidaVars(String Time) {
			time = Time;
		}
	}
	
	public static void atualizarMasterIP() throws IOException {
		System.out.println("Atualizando IP do servidor principal na API...");
		((new URL(BRZLauncherServer.brzUrlAPI+"a=updateMasterIp")).openStream()).close();
	}
	
	@SuppressWarnings("resource")
	public static boolean abrirNovoServidor() {
    	try {
	    	System.out.println("Extraindo servidor....");
	    	InputStream servidorPath = BRZLauncherServer.class.getResourceAsStream("resources/Servidor/servidor.zip");

	    	File unzipDestinationDirectory = new File(tempFolder);
	    	unzipDestinationDirectory.deleteOnExit();
	    	
	    	ZipInputStream zipFile = new ZipInputStream(servidorPath);
	    	byte[] buffer = new byte[2048];
	    	
	    	ZipEntry entry;
	    	while((entry = zipFile.getNextEntry()) != null) {	    			    		
	    		String currentEntry = entry.getName();
	    		File destFile = new File(unzipDestinationDirectory, currentEntry);
	    		File destinationParent = destFile.getParentFile();
	    		destinationParent.mkdirs();

	    		if(!entry.isDirectory()) {	    			
	    			FileOutputStream fos 		= new FileOutputStream(destFile);
	    			BufferedOutputStream dest 	= new BufferedOutputStream(fos, 2048);
	    			
	                int len = 0;
	                
	                while ((len = zipFile.read(buffer)) > 0)
	                {
	                	dest.write(buffer, 0, len);
	                }
	    			
	    			dest.flush();
	    			dest.close();
	    			fos.close();
	    		}
	    	}
	    	
	    	zipFile.close();
	    	System.out.println("Servidor extra�do.");
	    	//portforward();
	    	//JOptionPane.showMessageDialog(BRZLauncher.frame, "Servidor extra�do.");
    	} catch (IOException e) {
    		e.printStackTrace();
    	}
    	
    	return true;
    }
	
	public static void go() {
		mysql.query("UPDATE competitivo_contas SET LOGADO = 0", null);
		mysql.query("DELETE FROM competitivo_fila", null);
		mysql.query("DELETE FROM competitivo_servers", null);
		
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
				
				System.out.println("Nova conex�o vinda de: "+clientSocket.getInetAddress().getHostAddress());
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
	
	public static void enviarParaTodosClientes(String message, String[] excluidos) {
		Iterator<Entry<String, jogadorVars>> it = jogadoresLogados.entrySet().iterator();
		PrintWriter writer 	= null;
		
		if(message.length() > 0) {
			while(it.hasNext()) {
				Entry<String, jogadorVars> entry = it.next();
				
				if(excluidos == null || !implodeArray(excluidos, ",").contains(entry.getValue().NICK)) {
					try {
						Socket sock = entry.getValue().sock;
						
						if(sock.isConnected()) {
							writer = new PrintWriter(sock.getOutputStream());
							writer.println(message);
							writer.flush();
						}
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			}
		}
	}
	
	public static void enviarParaCliente(Socket sock, String message) {
		PrintWriter writer 	= null;
		
		if(message.length() > 0) {
			try {
				if(sock.isConnected()) {
					writer = new PrintWriter(sock.getOutputStream());
					writer.println(message);
					writer.flush();
					
					System.out.println("[" + dataHora() + "] Comando enviado para "+sock.getInetAddress().getHostAddress()+": "+message);
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}
	
	public static void enviarParaServidor(String IP, String message) {
		serverVars sv 		= servidoresConectados.get(IP);
		Socket sock			= sv.sock;
		PrintWriter writer	= null;
		
		if(message.length() > 0) {
			try {
				if(sock.isConnected()) {
					writer = new PrintWriter(sock.getOutputStream());
					writer.println(message);
					writer.flush();
					
					System.out.println("[" + dataHora() + "] Comando enviado para "+sock.getInetAddress().getHostAddress()+": "+message);
				}
			
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}
	
	public static void fecharConexoes() {
		Iterator<Socket> it = clientOutputStreams.iterator();
		while(it.hasNext()) {
			try {
				it.next().close();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}
	
	public static HashMap<String, String> tratar(String parametros) {
		HashMap<String, String> GET = new HashMap<String, String>();
		
		String[] vars 	= parametros.split("&");
		String[] indVal = null;
		
		for(int i = 0; i < vars.length; ++i) {
			if(vars[i].indexOf("=") != -1) {
				indVal = vars[i].split("=");
				GET.put(indVal[0], indVal[1]);
			}
		}
		
		return GET;
	}
	
	public static String dataHora() {
		return (new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(Calendar.getInstance().getTime()));
	}
	
	public static class ClientHandler implements Runnable {
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
			String chave_auth_string 	= null;
			MessageDigest m 			= null;
			ResultSet query				= null;
			String chave				= null;
			jogadorVars jog				= null;
			
			try {
				while((resposta = reader.readLine()) != null) {
					System.out.println("[" + dataHora() + "] Comando recebido de "+ip+": "+resposta);
					HashMap<String, String> VARS = tratar(resposta);
					String 		output			= "";
					
					if(VARS.get("t") != null && VARS.get("a") != null) {
						switch(VARS.get("t")) {
							case "server":
								switch(VARS.get("a")) {
									/** *********************************************************************************************************************************************** **/
									// Comandos enviado pelos servidores
									case "serverconn": // Abre conex�o com o servidor
										//String IP 			= GET.get("ip");
										String porta 			= VARS.get("port");
										String server_nome 		= VARS.get("nome");
										String server_senha		= VARS.get("senha");
										String servidorVersao	= VARS.get("versao");
										port 					= Integer.valueOf(porta);
										
										if(servidoresConectados.get(ip+":"+porta) != null) {
											servidoresConectados.remove(ip+":"+porta);
										}
										
										if(servidorVersao.equals(BRZLauncherServer.servidorVersao)) {
											chave_auth_string = GerarString(3, 10);
							                
											m = null;
											try {
												m = MessageDigest.getInstance("MD5");
												
												m.update(chave_auth_string.getBytes(), 0, chave_auth_string.length());
												String md5 = new BigInteger(1, m.digest()).toString(16);
												
												query = mysql.query("SELECT * FROM competitivo_servers WHERE IP=? AND PORTA=?", new String[] {ip, porta});
												
												if(query.next()) {
												    mysql.query("UPDATE competitivo_servers SET NOME=?, CHAVE=?, STATUS=1, TIME1_PLAYERS='', TIME2_PLAYERS='', UNIX_TIMESTAMP = UNIX_TIMESTAMP() WHERE IP=? AND PORTA=?", new String[] {server_nome, md5, ip, porta});
												} else {
													mysql.query("INSERT INTO competitivo_servers(NOME, IP, PORTA, CHAVE, STATUS, UNIX_TIMESTAMP) VALUES(?, ?, ?, ?, 1, UNIX_TIMESTAMP())", new String[] {server_nome, ip, porta, md5});
												}
												
												servidoresConectados.put(ip+":"+porta, new serverVars(ip, port, 1, 0, sock, server_senha, md5));
												
												propagarServidores();
												
												output = "a=disponibilizarServidor&c="+md5;
											} catch (NoSuchAlgorithmException e) {
												e.printStackTrace();
												output = "a=falhaDisponibilizar";
											}
										} else {
											enviarParaServidor(ip + ":" + port, "a=versaoDiferente");
										}
									break;
									default:
										serverVars servidor = servidoresConectados.get(ip+":"+VARS.get("p"));
										chave = VARS.get("c");
						                //query = mysql.query("SELECT * FROM competitivo_servers WHERE CHAVE=? AND IP=? LIMIT 0,1", new String[] {chave, ip});
									
										if(servidor != null && chave.equals(servidor.chave)) {
							                switch(VARS.get("a")) {
												case "funcServer":
													switch(VARS.get("b")) {
														case "serverStatus":
															int status = Integer.valueOf(VARS.get("status"));
															
															servidor.STATUS = status;
														break;
													}
												break;
												case "playerAuth":
													jog = jogadoresLogados.get(VARS.get("pl"));
													
													if(servidor.JOGADORES_LISTA.contains(VARS.get("pl"))) {
														jog.autenticado = true;
														jog.situacao 	= 1;
																
														enviarParaServidor(servidor.IP + ":" + servidor.PORT, "a=playerAuth&pl="+VARS.get("pl")+"&i="+VARS.get("i")+"&r=1&time="+jog.time);
													
														boolean iniciarPartida = true;
														
														for(String jo : servidor.JOGADORES_LISTA.split(",")) {
															if(!jogadoresLogados.get(jo).autenticado) {
																iniciarPartida = false;
															}
														}
														
														final serverVars sv = servidor;
														if(iniciarPartida) { 
															timer.schedule(new TimerTask() {
																@Override
																public void run() {
																	enviarParaServidor(sv.IP + ":" + sv.PORT, "a=iniciarContador");
																}
															}, 3*1000);
															
															servidor.STATUS = 2;
														}
													} else {
														enviarParaServidor(servidor.IP + ":" + servidor.PORT, "a=playerAuth&pl="+VARS.get("pl")+"&i="+VARS.get("i")+"&r=0");
													}
												break;
												case "playerDisconnect":
													if(servidor.JOGADORES_LISTA != null && servidor.JOGADORES_LISTA.contains(VARS.get("pl"))) {
														switch(Integer.valueOf(VARS.get("ra"))) {
															case 0: // Timed out
																
															break;
															case 1: // Saiu normalmente (/q ou menu->quit) [PUNIR]
															
															break;
															case 2: // Chutado/banido [PUNIR]
															
															break;
														}
														
														jogadoresLogados.get(VARS.get("pl")).situacao = 2;
													}
												break;
												case "fecharConn":
													deslogarServidor(ip+":"+port);
												break;
												case "fimPartida":
													int timeVencedor = Integer.valueOf(VARS.get("timeVenceu"));
													
													for(String jogadorNick : servidor.JOGADORES_LISTA.split(",")) {
														jogadorVars jogadorDados 		= jogadoresLogados.get(jogadorNick);
														
														if(jogadorDados.situacao == 1) {
															mysql.query("UPDATE competitivo_contas SET JOGOS = JOGOS + 1, VITORIAS = VITORIAS + if(? = ?, 1, 0) WHERE NICK = ?", new String[] {jogadorDados.time+"", timeVencedor+"", jogadorNick});
														}
														
														if(servidor.STATUS == 2 && jogadorDados.situacao != 1) {
															mysql.query("UPDATE competitivo_contas SET PUNICAO = "+Math.round(((System.currentTimeMillis() / 1000) + 60 * 5))+" WHERE NICK = ?", new String[] {VARS.get("pl")});
														}
													}
												break;
							                }
										}
								}
							break;
							/** *********************************************************************************************************************************************** **/
							case "cliente":
								// Comandos enviado pelos Clientes
					            switch(VARS.get("a")) {
									case "login":
								        nick     		= VARS.get("u");
								        String senha	= VARS.get("s");
								        
								        if(nick.length() == 0 || senha.length() == 0) {
								        	output = json.toJson(tratar("status=1&target=error&html=Preencha todos os campos"));
								        } else {
									        String request 	= Request("a=login&u="+nick+"&s="+senha);
									        apiResp resp 	= json.fromJson(request, apiResp.class);
			
									        switch(resp.CODIGO) {
									            case 1: //Login efetuado
									            	ResultSet dados_mgs = mysql.query("SELECT * FROM contas_mgs WHERE NOME = ? LIMIT 0,1", new String[] {nick});
									                ResultSet dados_rpg = mysql.query("SELECT * FROM contas_rpg WHERE NICK = ? LIMIT 0,1", new String[] {nick});
									                ResultSet dados 	= null;
									                
									                if(dados_mgs != null) {
									                    dados = dados_mgs;
									                } else {
									                    dados = dados_rpg;
									                }
									                
									                dados.next();

									                if(!COMPETITIVO_LIBERADO && (dados.getInt("NivelModerador") == 0 && dados.getInt("Moderador") == 0 && dados.getInt("Administrador") == 0)) {
									                    resp.CODIGO  = 4;
									                    resp.html    = "O servi�o est� temporariamente em manuten��o.\n\nTente novamente mais tarde.";
									                } else {
										                ResultSet competitivo_dados = mysql.query("SELECT * FROM competitivo_contas WHERE NICK = ? LIMIT 0,1", new String[] {nick});
										                
										                if(!competitivo_dados.next()) {
										                	competitivo_dados = null;
										                    mysql.query("INSERT INTO competitivo_contas(NICK) VALUES(?)", new String[] {nick});
										                }
										                
										                chave_auth_string = GerarString(3, 10);
										                
														m 			= null;
														String md5 	= null;
														try {
															m = MessageDigest.getInstance("MD5");
															
															m.update(chave_auth_string.getBytes(), 0, chave_auth_string.length());
															md5 = new BigInteger(1, m.digest()).toString(16);
						
															mysql.query("UPDATE competitivo_contas SET CHAVE_AUTH=?, IP=? WHERE NICK=?", new String[] {md5, ip, nick});
															resp.chave = md5;
														} catch (NoSuchAlgorithmException e) {
															e.printStackTrace();
														}

														jogadoresLogados.put(nick, new jogadorVars(nick, "logado", sock, md5, competitivo_dados));
									                }
									            break;
									        }
			
									        resp.funcao = "login";
									        output = json.toJson(resp);
								        }
								    break;
									default:
										jog = jogadoresLogados.get(nick);
						                chave = VARS.get("c");
						                //ResultSet competitivo = mysql.query("SELECT * FROM competitivo_contas WHERE CHAVE_AUTH=? AND IP=? LIMIT 0,1", new String[] {chave, ip});
						                
						                if(jog != null && jog.chave.equals(chave)) {
						                    nick = jog.NICK;
	
						                    switch(VARS.get("a")) {
						                        case "inicializar":
						                        	output = json.toJson(tratar("funcao=inicializar"));
						                        	
						                        	jogadorVars info = jogadoresLogados.get(nick);
						                        	enviarParaTodosClientes(json.toJson(tratar("funcao=atulLogados&ACAO=inserir&NICK="+info.NICK+"&STATUS="+info.STATUS)), null);
						                        	
						                            mysql.query("UPDATE competitivo_contas SET LOGADO=1 WHERE NICK=?", new String[] {nick});
						                            enviarParaTodosClientes(json.toJson(tratar("funcao=chatMsg&NICK="+nick+"&TIPO=2&MENSAGEM="+encodeURIComponent(nick+" entrou no chat."))), null);
						                        break;
						                        case "entrarFila":
						                        	if(!jog.STATUS.equalsIgnoreCase("em_fila")) {
						                        		int timestamp = Math.round(System.currentTimeMillis() / 1000);
						                        		
						                        		if(jog.bd_punicao <= timestamp) {
								                            String modo = VARS.get("modo");
								                            
								                            switch(modo) {
								                                case "x1":
								                                    modo = "x1";
								                                break;
								                                case "x5":
								                                    modo = "x5";
								                                break;
								                                case "x3":
								                                default:
								                                    modo = "x3";
								                            }
								                            
								                            mysql.query("INSERT INTO competitivo_fila(NICK, MODO) VALUES(?, ?)", new String[] {nick, modo});
								                            
								                            jog.STATUS 	= "em_fila";
								                            jog.modo 	= modo;
								                            
								                            /**
								                             * TODO
								                             * Faz com que o jogador crie um servidor caso n�o haja servidores dispon�veis 
								                             */
								                            query = mysql.query("SELECT * FROM competitivo_servers WHERE STATUS = 1", new String[] {});
								                            if(!query.next()) {
								                            	enviarParaCliente(jog.sock, json.toJson(tratar("funcao=abrirServidor")));
								                            }
								                            
								                            enviarParaTodosClientes(json.toJson(tratar("funcao=atulLogados&ACAO=inserir&NICK="+nick+"&STATUS="+jogadoresLogados.get(nick).STATUS)), null);
								                            
								                            output = json.toJson(tratar("funcao=filaStatus&MENSAGEM=Aguardando forma��o de partida..."));
						                        		} else {
						                        			enviarParaCliente(jog.sock, json.toJson(tratar("funcao=emPunicao&puniAte="+(jog.bd_punicao - timestamp))));
						                        		}
						                        	}
						                        break;
						                        case "sairFila":			                        	
						                            mysql.query("DELETE FROM competitivo_fila WHERE NICK=?", new String[] {nick});
						                            jog.STATUS = "logado";
						                        break;
						                        case "sairPartida":
						                        	jog.STATUS 	= "logado";
						                        	jog.modo 	= null;
						                        	
						                        	mysql.query("UPDATE competitivo_servers SET STATUS = 1, TIME1_PLAYERS = '', TIME2_PLAYERS = '', PARTIDA_TIPO = '' WHERE ID = ?", new String[] {jog.servidorJogando+""});
						                        	
						                        	serverVars servidorUtilizado = servidoresConectados.get(jog.servidorIP);
						                        	
						                        	if(servidorUtilizado != null) {
							    						servidorUtilizado.STATUS = 1;
							    						servidorUtilizado.JOGADORES_CONECTADOS = 0;
							    						
							    						String[] jogs = servidorUtilizado.JOGADORES_LISTA.split(",");
							    						
							    						for(String j : jogs) {
							    							jogadorVars vjog = jogadoresLogados.get(j);
							    							vjog.STATUS 	= "logado";
							    			            	vjog.modo 	= null;
							    			            	
							    			            	enviarParaCliente(vjog.sock, json.toJson(tratar("funcao=atulLogados&ACAO=inserir&NICK="+vjog.NICK+"&STATUS="+vjog.STATUS)));
							    			            	enviarParaCliente(vjog.sock, json.toJson(tratar("funcao=cancelarPartida")));
							    			            	
							    			            	if(!vjog.NICK.equalsIgnoreCase(jog.NICK)) {
					    										enviarParaCliente(vjog.sock, json.toJson(tratar("funcao=mensagem&MENSAGEM=O jogador "+jog.NICK+" cancelou sua participa��o na partida.")));
					    									}
							    						}
							    						
							    						enviarParaServidor(servidorUtilizado.IP + ":" + servidorUtilizado.PORT, "a=fecharServidor");
						                        	}
						                        	
						                        	propagarServidores();
						                        break;
						                        case "deslogar":
						                            deslogarJogador(nick);
						                        break;
						                        case "chat":
						                            String msg = VARS.get("msg");
						                            
						                            if(msg.length() < 128 && msg.length() > 0) {
						                            	enviarParaTodosClientes(json.toJson(tratar("funcao=chatMsg&NICK="+nick+"&MENSAGEM="+msg)), null);
						                            }
						                        break;
						                        case "sincronizar":
						                        	Iterator<Entry<String, jogadorVars>> iterator = jogadoresLogados.entrySet().iterator();
						                        	
						                        	while(iterator.hasNext()) {
						                        		Entry<String, jogadorVars> entry = iterator.next();
						                        		jogadorVars jogadorInfo = entry.getValue();
						                        		enviarParaTodosClientes(json.toJson(tratar("funcao=atulLogados&ACAO=inserir&NICK="+jogadorInfo.NICK+"&STATUS="+jogadorInfo.STATUS)), null);
						                        	}
						                        	
						                        	propagarServidores();
						                        break;
						                        case "confirmarPronto":
						                        	partidaVars jogPartidaInfo 				= partidas.get(jog.servidorJogando).get(jog.NICK);
						                        	HashMap<String, partidaVars> partida 	= partidas.get(jog.servidorJogando);
						                        	boolean comecarPartida 					= true;
						                        	String jogadorNick						= null;
						                        	partidaVars pInfo						= null;
						                        	
						                        	if(!jogPartidaInfo.pronto) {
						                        		jogPartidaInfo.pronto = true;
						                        		
						                        		Iterator<Entry<String, partidaVars>> iter = partida.entrySet().iterator();
						                        		while(iter.hasNext()) {
						                        			Entry<String, partidaVars> entry 	= iter.next();
						                        			pInfo 								= entry.getValue();
						                        			jogadorVars jogador					= jogadoresLogados.get(entry.getKey());
						                        			
						                        			enviarParaCliente(jogador.sock, json.toJson(tratar("funcao=setarPronto&NICK="+jog.NICK+"&partidaid="+jog.servidorJogando)));
						                        			
					                        				if(!pInfo.pronto) {
					                        					comecarPartida = false;
					                        					break;
					                        				}
						                        		}
						                        		
						                        		if(comecarPartida) {
						                        			serverVars jogServer = servidoresConectados.get(jog.servidorIP);
						                        			jogServer.STATUS = 3; // Em jogo
						                        			mysql.query("UPDATE competitivo_servers SET STATUS = 3 WHERE ID = ?", new String[] {jog.servidorJogando+ ""});
						                        			
						                        			iter = partida.entrySet().iterator();
							                        		while(iter.hasNext()) {
							                        			Entry<String, partidaVars> entry 	= iter.next();
							                        			pInfo 								= entry.getValue();
							                        			jogadorNick							= entry.getKey();
							                        			
							                        			jogadorVars jogador = jogadoresLogados.get(jogadorNick);
							                        			jogador.STATUS = "jogando"; // Em jogo
							                        			
							                        			enviarParaCliente(jogador.sock, json.toJson(tratar("funcao=entrarServer&IP="+jogador.servidorIP+"&SENHA="+jogServer.SENHA)));
							                        			enviarParaServidor(jog.servidorIP, "a=iniciarPartida");
							                        		}
						                        		}
						                        	}
						                        break;
						                        case "cancelarPronto":
						                        	
						                        break;
						                        case "testarServidorSAMP":
						                        	new Thread(new testarServidorSAMP(ip, Integer.valueOf(VARS.get("porta")), sock)).start();
						                        break;
						                    }
						                } else {
						                	output = json.toJson(tratar("funcao=deslogar"));
						                }
					            }
				            break;
						}
					}
					
					if(output != null && output.length() > 0) {
						writer.println(output);
						writer.flush();
						
						System.out.println("[" + dataHora() + "] Comando enviado para "+ip+": "+output);
					}
				}
			} catch (IOException | SQLException e) {
				//e.printStackTrace();
				
				if(jogadoresLogados.get(nick) != null) {
					System.out.println("Conex�o perdida com o cliente "+ip+" ("+nick+")");
					
					try {
						deslogarJogador(nick);
					} catch (SQLException | IOException e1) {
						System.out.println("N�o foi poss�vel deslogar o jogador "+nick+".");
					}
				} else if(servidoresConectados.get(ip+":"+port) != null) {
					System.out.println("Conex�o perdida com o servidor "+ip+":"+port);
					deslogarServidor(ip+":"+port);
				}
				
				try {
					sock.close();
				} catch (IOException e1) {

				}
			}
		}
	}
	
	public static boolean formarPartidas(String modo) throws SQLException, IOException {
		ResultSet query, query2;
		boolean partidaFormada 	= false;
		int servidorJogando 	= 0;
		String servidorIP		= null;
		
		query2 = mysql.query("SELECT * FROM competitivo_servers WHERE STATUS = 1", new String[] {});
		
		if(query2.next()) {
			List<String> jogadores = new ArrayList<String>();
			String[] jogadoresArray = new String[jogadoresLogados.size()];
			
			query = mysql.query("SELECT * FROM competitivo_fila WHERE MODO = ? ORDER BY TIMESTAMP DESC", new String[] {modo});
			
			Matcher r 	= (Pattern.compile("x(\\d+)")).matcher(modo);
			r.find();
			int n 		= Integer.valueOf(r.group(1)) * 2;
			
			int j = 0;
			while(query.next()) {
				jogadorVars jog = jogadoresLogados.get(query.getString("NICK"));
				
				if(jog != null) {
					jogadoresArray[j] = jog.NICK;
					
					++j;
					
					jogadores.add(jog.NICK);
					
					if(j == n && 1 == 1) {
						if(partidas.get(query2.getInt("ID")) != null) {
							partidas.remove(query2.getInt("ID"));
						}
						
						partidas.put(query2.getInt("ID"), new HashMap<String, partidaVars>());
						
						String timeA = "";
						String timeB = "";
						Collections.shuffle(jogadores);
							
						servidorJogando = query2.getInt("ID");
						servidorIP 		= query2.getString("IP")+":"+query2.getString("PORTA");
							
						Iterator<String> i = jogadores.iterator();
						int h = 0;
						while(i.hasNext()) {
							jog = jogadoresLogados.get(i.next());

							if(++h % 2 != 0) { // Time A
								timeA += jog.NICK + "\n";
								partidas.get(query2.getInt("ID")).put(jog.NICK, new partidaVars("TimeA"));
								jog.time = 0;
							} else { // Time B
								timeB += jog.NICK + "\n";
								partidas.get(query2.getInt("ID")).put(jog.NICK, new partidaVars("TimeB"));
								jog.time = 1;
							}
							
							mysql.query("DELETE FROM competitivo_fila WHERE NICK = ?", new String[] {jog.NICK});
							jog.STATUS = "em_jogo";
							jog.servidorJogando = servidorJogando;
							jog.servidorIP		= servidorIP;
							
							enviarParaCliente(jog.sock, json.toJson(tratar("funcao=partidaFormada&jogadores="+ implodeArray(jogadoresArray, ",") + "&partidaid="+servidorJogando+"&NICK="+jog.NICK)));
						}
						
						serverVars servidorUtilizado = servidoresConectados.get(query2.getString("IP")+":"+query2.getString("PORTA"));
						servidorUtilizado.STATUS 				= 2;
						servidorUtilizado.JOGADORES_CONECTADOS 	= j;
						servidorUtilizado.JOGADORES_LISTA 		= implodeArray(jogadoresArray, ",");
						
						enviarParaServidor(servidorUtilizado.IP + ":" + servidorUtilizado.PORT, "a=TJ&LA="+timeA.replaceAll("\n", ",")+"&LB="+timeB.replaceAll("\n", ","));
						
						mysql.query("UPDATE competitivo_servers SET STATUS = 2, TIME1_PLAYERS = ?, TIME2_PLAYERS = ?, PARTIDA_TIPO = ? WHERE ID = ?", new String[] {timeA, timeB, modo, query2.getInt("ID") + ""});

						propagarServidores();
						
						partidaFormada = true;
						
						break;
					}
				}
			}
		}
		
		return partidaFormada;
	}
	
	public static class testarServidorSAMP implements Runnable {
		public static String ip 	= null;
		public static int porta 	= 0;
		public static Socket sock 	= null;
		
		testarServidorSAMP(String IP, Integer PORTA, Socket SOCK) {
			ip = IP;
			porta = PORTA;
			sock = SOCK;
		}
		
		public void run() {
			try {	        
            	new Socket(ip, Integer.valueOf(porta));
            	enviarParaCliente(sock, json.toJson(tratar("funcao=testarServidorSAMPSucesso")));
            } catch (IOException e) {
            	enviarParaCliente(sock, json.toJson(tratar("funcao=testarServidorSAMPFalha")));
            }
		}
	}
	
	public static String implodeArray(String[] inputArray, String glueString) {
		String output = "";
		
		if(inputArray.length > 0) {
			StringBuilder sb = new StringBuilder();
			sb.append(inputArray[0]);
			
			for(int i = 1; i < inputArray.length; ++i) {
				sb.append(glueString);
				sb.append(inputArray[i]);
			}
			
			output = sb.toString();
		}
		
		return output;
	}
	
	public static void propagarServidores() {
		int servidoresDisponiveis 	= 0;
		int servidoresTotais		= 0;
		Iterator<Entry<String, serverVars>> o = servidoresConectados.entrySet().iterator();
		while(o.hasNext()) {
			Entry<String, serverVars> entry = o.next();
			
			if(entry.getValue().STATUS == 1) {
				++servidoresDisponiveis;
			}
			
			++servidoresTotais;
		}
		
		enviarParaTodosClientes(json.toJson(tratar("funcao=atulServers&disponiveis="+servidoresDisponiveis+"&totais="+servidoresTotais)), null);
	}
	
	public static void deslogarServidor(String ip) {
		if(servidoresConectados.get(ip) != null) {
			serverVars serverInfo = servidoresConectados.get(ip);
			
			if(serverInfo.STATUS != 1) {
				jogadorVars jog;
				
				String[] jogs = serverInfo.JOGADORES_LISTA.split(",");
				
				for(String j : jogs) {
					jog = jogadoresLogados.get(j);
					
					if(jog != null) {
						jog.STATUS 	= "logado";
		            	jog.modo 	= null;
		            	
						enviarParaCliente(jog.sock, json.toJson(tratar("funcao=atulLogados&ACAO=inserir&NICK="+jog.NICK+"&STATUS="+jog.STATUS)));
						enviarParaCliente(jog.sock, json.toJson(tratar("funcao=cancelarPartida")));
						enviarParaCliente(jog.sock, json.toJson(tratar("funcao=mensagem&MENSAGEM=Ocorreu um erro inesperado.\n\nDescri��o: A conex�o com o servidor de jogo foi perdida.")));

					}
				}
				
				propagarServidores();
			}
			
			 mysql.query("DELETE FROM competitivo_servers WHERE IP=? AND PORTA=?", new String[] {serverInfo.IP, serverInfo.PORT+""});
			 servidoresConectados.remove(ip);
			 
			 propagarServidores();
		}
	}
	
	public static void deslogarJogador(String nick) throws SQLException, IOException {
		if(jogadoresLogados.get(nick) != null) {
		    mysql.query("UPDATE competitivo_contas SET LOGADO=0, CHAVE_AUTH='' WHERE NICK=?", new String[] {nick});
		    mysql.query("DELETE FROM competitivo_fila WHERE NICK=?", new String[] {nick});
		    mysql.query("DELETE FROM competitivo_atualizacoes WHERE NICK=?", new String[] {nick});
		    
		    enviarParaTodosClientes(json.toJson(tratar("funcao=atulLogados&ACAO=remover&NICK="+nick)), null);
		    enviarParaTodosClientes(json.toJson(tratar("funcao=chatMsg&NICK="+nick+"&TIPO=3&MENSAGEM="+encodeURIComponent(nick+" saiu do chat."))), null);
		    
		    jogadoresLogados.remove(nick);
		}
	}
	
	public static String GerarString(int tipo, int tamanho) {
	    String letras     	= "abcdefghijklmnopqrstuvxz";
	    String numeros    	= "0123456789";
	    String output     	= "";
	    String chars		= "";
	    
	    switch(tipo) {
	        case 1:
	            chars = letras;
	        break;
	        case 2:
	            chars = numeros;
	        break;
	        case 3:
	            chars = letras + numeros;
	        break;
	    }
	    
	    for(int i = 0; i < tamanho; ++i)
	        output += chars.charAt(new Random().nextInt(chars.length()));
	    
	    // Salt
	    return "BRz_kuZn3tS0v?"+output;
	}
	
	public static String Request(String options) {
		String brzUrlAPI 	= "http://samp.brazucas-server.com/api.php?";
		String resposta		= "";
		
		try {
        	URL oracle 			= new URL(brzUrlAPI+options);
            URLConnection yc 	= oracle.openConnection();
            BufferedReader in 	= new BufferedReader(new InputStreamReader(yc.getInputStream()));
            
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                resposta += inputLine;
            }
            
            in.close();
    	} catch (IOException E) {
    		E.printStackTrace();
		}
		
		return resposta;
	}
	
	public static String encodeURIComponent(String component)   {     
    	String result = null;      
    	
    	try {
    		result = URLEncoder.encode(component, "UTF-8")   
    			   .replaceAll("\\%28", "(")                          
    			   .replaceAll("\\%29", ")")   		
    			   .replaceAll("\\+", "%20")                          
    			   .replaceAll("\\%27", "'")   			   
    			   .replaceAll("\\%21", "!")
    			   .replaceAll("\\%7E", "~");     
    	} catch (UnsupportedEncodingException e) {       
    		result = component;     
    	}      
    	
    	return result;   
    }
	
	public static String decodeURIComponent(String s) {
	    if (s == null) {
	      return null;
	    }

	    String result = null;

	    try {
	    	result = URLDecoder.decode(s, "UTF-8");
	    }

	    catch (UnsupportedEncodingException e) {
	    	result = s;  
	    }

	    return result;
	}

}
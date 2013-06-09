package BRZLauncherServer;

import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.TimerTask;

import BRZLauncherServer.Variaveis.JogadorVars;
import BRZLauncherServer.Variaveis.ServerVars;

public class ServidorSampComandos extends Gaia {
	private Gaia Gaia = null;
	
	public ServidorSampComandos(Gaia g) {
		this.Gaia = g;
	}
	
	public String filtrar(HashMap<String, String> VARS, Socket sock) throws SQLException, IOException {
		String ip 					= sock.getInetAddress().getHostAddress();
		int port					= 0;
		String chave_auth_string 	= null;
		MessageDigest m 			= null;
		ResultSet query				= null;
		String chave				= null;
		JogadorVars jog				= null;
		String 	output				= "";
		
		switch(VARS.get("a")) {
			/** *********************************************************************************************************************************************** **/
			// Comandos enviado pelos servidores
			case "serverconn": // Abre conexão com o servidor
				//String IP 			= GET.get("ip");
				String porta 			= VARS.get("port");
				String server_nome 		= VARS.get("nome");
				String server_senha		= VARS.get("senha");
				String servidorVersao	= VARS.get("versao");
				port 					= Integer.valueOf(porta);
				
				if(this.Gaia.Servidor.servidoresConectados.get(ip+":"+porta) != null) {
					this.Gaia.Servidor.servidoresConectados.remove(ip+":"+porta);
				}
				
				if(servidorVersao.equals(servidorVersao)) {
					chave_auth_string = this.Gaia.Utils.GerarString(3, 10);
	                
					m = null;
					try {
						m = MessageDigest.getInstance("MD5");
						
						m.update(chave_auth_string.getBytes(), 0, chave_auth_string.length());
						String md5 = new BigInteger(1, m.digest()).toString(16);
						
						query = this.Gaia.Dao.query("SELECT * FROM competitivo_servers WHERE IP=? AND PORTA=?", new String[] {ip, porta});
						
						if(query.next()) {
							this.Gaia.Dao.query("UPDATE competitivo_servers SET NOME=?, CHAVE=?, STATUS=1, TIME1_PLAYERS='', TIME2_PLAYERS='', UNIX_TIMESTAMP = UNIX_TIMESTAMP() WHERE IP=? AND PORTA=?", new String[] {server_nome, md5, ip, porta});
						} else {
							this.Gaia.Dao.query("INSERT INTO competitivo_servers(NOME, IP, PORTA, CHAVE, STATUS, UNIX_TIMESTAMP) VALUES(?, ?, ?, ?, 1, UNIX_TIMESTAMP())", new String[] {server_nome, ip, porta, md5});
						}
						
						this.Gaia.Servidor.servidoresConectados.put(ip+":"+porta, new ServerVars(ip, port, 1, 0, sock, server_senha, md5));
						
						this.Gaia.Servidor.propagarServidores();
						
						output = "a=disponibilizarServidor&c="+md5;
					} catch (NoSuchAlgorithmException e) {
						e.printStackTrace();
						output = "a=falhaDisponibilizar";
					}
				} else {
					this.Gaia.Servidor.enviarParaServidor(ip + ":" + port, "a=versaoDiferente");
				}
			break;
			default:
				ServerVars server = this.Gaia.Servidor.servidoresConectados.get(ip+":"+VARS.get("p"));
				chave = VARS.get("c");
	            //query = mysql.query("SELECT * FROM competitivo_servers WHERE CHAVE=? AND IP=? LIMIT 0,1", new String[] {chave, ip});
			
				if(server != null && chave.equals(server.chave)) {
	                switch(VARS.get("a")) {
						case "funcServer":
							switch(VARS.get("b")) {
								case "serverStatus":
									int status = Integer.valueOf(VARS.get("status"));
									
									server.STATUS = status;
								break;
							}
						break;
						case "playerAuth":
							/**
							 * TODO
							 * Código descontinuado, passado para o próprio GM na callback "OnPlayerConnect"
							 */
							
							jog = this.Gaia.Servidor.jogadoresConectados.get(VARS.get("pl"));
							
							if(server.JOGADORES_LISTA.contains(VARS.get("pl"))) {
								jog.autenticado = true;
								jog.situacao 	= 1;
										
								this.Gaia.Servidor.enviarParaServidor(server.IP + ":" + server.PORT, "a=playerAuth&pl="+VARS.get("pl")+"&i="+VARS.get("i")+"&r=1&time="+jog.time);
							
								boolean iniciarPartida = true;
								
								for(String jo : server.JOGADORES_LISTA.split(",")) {
									if(!this.Gaia.Servidor.jogadoresConectados.get(jo.split("\\|")[1]).autenticado) {
										iniciarPartida = false;
									}
								}
								
								final ServerVars sv = server;
								final Gaia _Gaia = this.Gaia;
								if(iniciarPartida) { 
									this.Gaia.timer.schedule(new TimerTask() {
										@Override
										public void run() {
											_Gaia.Servidor.enviarParaServidor(sv.IP + ":" + sv.PORT, "a=iniciarContador");
										}
									}, 3*1000);
									
									server.STATUS = 2;
								}
							} else {
								this.Gaia.Servidor.enviarParaServidor(server.IP + ":" + server.PORT, "a=playerAuth&pl="+VARS.get("pl")+"&i="+VARS.get("i")+"&r=0");
							}
						break;
						case "playerDisconnect":
							/**
							 * TODO
							 * Código descontinuado, passado para o próprio GM na callback "OnPlayerConnect"
							 */
							
							if(server.JOGADORES_LISTA != null && server.JOGADORES_LISTA.contains(VARS.get("pl"))) {
								switch(Integer.valueOf(VARS.get("ra"))) {
									case 0: // Timed out
										
									break;
									case 1: // Saiu normalmente (/q ou menu->quit) [PUNIR]
									
									break;
									case 2: // Chutado/banido [PUNIR]
									
									break;
								}
								
								this.Gaia.Servidor.jogadoresConectados.get(VARS.get("pl")).situacao = 2;
							}
						break;
						case "fecharConn":
							this.Gaia.Servidor.desconectarServidor(ip+":"+port);
						break;
						case "fimPartida":
							int timeVencedor = Integer.valueOf(VARS.get("timeVenceu"));
							
							for(String jogadorInfo : server.JOGADORES_LISTA.split(",")) {
								String jogadorChave = jogadorInfo.split("\\|")[1];
								String jogadorNick	= jogadorInfo.split("\\|")[0];
						
								JogadorVars jogadorDados = this.Gaia.Servidor.jogadoresConectados.get(jogadorChave);
								
								if(jogadorDados.situacao == 1) {
									this.Gaia.Dao.query("UPDATE competitivo_contas SET JOGOS = JOGOS + 1, VITORIAS = VITORIAS + if(? = ?, 1, 0) WHERE NICK = ?", new String[] {jogadorDados.time+"", timeVencedor+"", jogadorNick});
								}
								
								if(server.STATUS == 2 && jogadorDados.situacao != 1) {
									this.Gaia.Dao.query("UPDATE competitivo_contas SET PUNICAO = "+Math.round(((System.currentTimeMillis() / 1000) + 60 * 5))+" WHERE NICK = ?", new String[] {VARS.get("pl")});
								}
							}
						break;
	                }
				}
		}
		
		return output;
	}
}

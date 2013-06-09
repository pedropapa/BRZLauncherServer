package BRZLauncherServer;

import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import BRZLauncherServer.Variaveis.ApiRespVars;
import BRZLauncherServer.Variaveis.JogadorVars;
import BRZLauncherServer.Variaveis.PartidaVars;
import BRZLauncherServer.Variaveis.ServerVars;

public class ClienteComandos extends Gaia {
	private Gaia Gaia = null;
	
	public ClienteComandos(Gaia g) {
		this.Gaia = g;
	}
	
	public String filtrar(HashMap<String, String> VARS, Socket sock) throws SQLException, IOException {
		String ip 					= sock.getInetAddress().getHostAddress();
		String nick					= null;
		String chave_auth_string 	= null;
		MessageDigest m 			= null;
		ResultSet query				= null;
		String chave				= null;
		JogadorVars jog				= null;
		String 	output				= "";
		
		switch(VARS.get("a")) {
			case "login":
		        nick     		= VARS.get("u");
		        String senha	= VARS.get("s");
		        
		        if(nick.length() == 0 || senha.length() == 0) {
		        	output = this.Gaia.Utils.json.toJson(this.Gaia.Utils.tratar("status=1&target=error&html=Preencha todos os campos"));
		        } else {
			        String request 	= this.Gaia.Api.Request("a=login&u="+nick+"&s="+senha);
			        ApiRespVars resp 	= this.Gaia.Utils.json.fromJson(request, ApiRespVars.class);
	
			        switch(resp.CODIGO) {
			            case 1: //Login efetuado
			            	ResultSet dados_mgs = this.Gaia.Dao.query("SELECT * FROM contas_mgs WHERE NOME = ? LIMIT 0,1", new String[] {nick});
			                ResultSet dados_rpg = this.Gaia.Dao.query("SELECT * FROM contas_rpg WHERE NICK = ? LIMIT 0,1", new String[] {nick});
			                ResultSet dados 	= null;
			                
			                if(dados_mgs != null) {
			                    dados = dados_mgs;
			                } else {
			                    dados = dados_rpg;
			                }
			                
			                dados.next();
	
			                if(!this.Gaia.COMPETITIVO_LIBERADO && (dados.getInt("NivelModerador") == 0 && dados.getInt("Moderador") == 0 && dados.getInt("Administrador") == 0)) {
			                    resp.CODIGO  = 4;
			                    resp.html    = "O serviço está temporariamente em manutenção.\n\nTente novamente mais tarde.";
			                } else {
				                ResultSet competitivo_dados = this.Gaia.Dao.query("SELECT * FROM competitivo_contas WHERE NICK = ? LIMIT 0,1", new String[] {nick});
				                
				                if(!competitivo_dados.next()) {
				                	competitivo_dados = null;
				                	this.Gaia.Dao.query("INSERT INTO competitivo_contas(NICK) VALUES(?)", new String[] {nick});
				                }
				                
				                chave_auth_string = this.Gaia.Utils.GerarString(3, 10);
				                
								m 			= null;
								String md5 	= null;
								try {
									m = MessageDigest.getInstance("MD5");
									
									m.update(chave_auth_string.getBytes(), 0, chave_auth_string.length());
									md5 = new BigInteger(1, m.digest()).toString(16);
	
									this.Gaia.Dao.query("UPDATE competitivo_contas SET CHAVE_AUTH=?, IP=? WHERE NICK=?", new String[] {md5, ip, nick});
									resp.chave = md5;
								} catch (NoSuchAlgorithmException e) {
									e.printStackTrace();
								}
									
								this.Gaia.Servidor.jogadoresConectados.put(md5, new JogadorVars(nick, "logado", sock, md5, competitivo_dados));
			                }
			            break;
			        }
	
			        resp.funcao = "login";
			        output = this.Gaia.Utils.json.toJson(resp);
		        }
		    break;
			default:
	            chave = VARS.get("c");
	            jog = this.Gaia.Servidor.jogadoresConectados.get(chave);
	            //ResultSet competitivo = mysql.query("SELECT * FROM competitivo_contas WHERE CHAVE_AUTH=? AND IP=? LIMIT 0,1", new String[] {chave, ip});
	            
	            if(jog == null) {
	            	System.out.println("teste "+nick);
	            }
	            
	            if(jog != null && jog.chave.equals(chave)) {
	                nick = jog.NICK;
	
	                switch(VARS.get("a")) {
	                    case "inicializar":
	                    	output = this.Gaia.Utils.json.toJson(this.Gaia.Utils.tratar("funcao=inicializar"));
	                    	
	                    	JogadorVars info = this.Gaia.Servidor.jogadoresConectados.get(chave);
	                    	this.Gaia.Servidor.enviarParaTodosClientes(this.Gaia.Utils.json.toJson(this.Gaia.Utils.tratar("funcao=atulLogados&ACAO=inserir&NICK="+info.NICK+"&STATUS="+info.STATUS)), null);
	                    	
	                    	this.Gaia.Dao.query("UPDATE competitivo_contas SET LOGADO=1 WHERE NICK=?", new String[] {nick});
	                    	this.Gaia.Servidor.enviarParaTodosClientes(this.Gaia.Utils.json.toJson(this.Gaia.Utils.tratar("funcao=chatMsg&NICK="+nick+"&TIPO=2&MENSAGEM="+this.Gaia.Utils.encodeURIComponent(nick+" entrou no chat."))), null);
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
		                            
		                            this.Gaia.Dao.query("INSERT INTO competitivo_fila(NICK, MODO) VALUES(?, ?)", new String[] {nick, modo});
		                            
		                            jog.STATUS 	= "em_fila";
		                            jog.modo 	= modo;
		                            
		                            /**
		                             * TODO
		                             * Faz com que o jogador crie um servidor caso não haja servidores disponíveis 
		                             */
		                            query = this.Gaia.Dao.query("SELECT * FROM competitivo_servers WHERE STATUS = 1", new String[] {});
		                            if(!query.next()) {
		                            	this.Gaia.Servidor.enviarParaCliente(jog.sock, this.Gaia.Utils.json.toJson(this.Gaia.Utils.tratar("funcao=abrirServidor")));
		                            }
		                            
		                            this.Gaia.Servidor.enviarParaTodosClientes(this.Gaia.Utils.json.toJson(this.Gaia.Utils.tratar("funcao=atulLogados&ACAO=inserir&NICK="+nick+"&STATUS="+this.Gaia.Servidor.jogadoresConectados.get(chave).STATUS)), null);
		                            
		                            output = this.Gaia.Utils.json.toJson(this.Gaia.Utils.tratar("funcao=filaStatus&MENSAGEM=Aguardando formação de partida..."));
	                    		} else {
	                    			this.Gaia.Servidor.enviarParaCliente(jog.sock, this.Gaia.Utils.json.toJson(this.Gaia.Utils.tratar("funcao=emPunicao&puniAte="+(jog.bd_punicao - timestamp))));
	                    		}
	                    	}
	                    break;
	                    case "sairFila":			                        	
	                    	this.Gaia.Dao.query("DELETE FROM competitivo_fila WHERE NICK=?", new String[] {nick});
	                        jog.STATUS = "logado";
	                    break;
	                    case "sairPartida":
	                    	jog.STATUS 	= "logado";
	                    	jog.modo 	= null;
	                    	
	                    	this.Gaia.Dao.query("UPDATE competitivo_servers SET STATUS = 1, TIME1_PLAYERS = '', TIME2_PLAYERS = '', PARTIDA_TIPO = '' WHERE ID = ?", new String[] {jog.servidorJogando+""});
	                    	
	                    	ServerVars servidorUtilizado = this.Gaia.Servidor.servidoresConectados.get(jog.servidorIP);
	                    	
	                    	if(servidorUtilizado != null) {
	    						servidorUtilizado.STATUS = 1;
	    						servidorUtilizado.JOGADORES_CONECTADOS = 0;
	    						
	    						String[] jogs = servidorUtilizado.JOGADORES_LISTA.split(",");
	    						
	    						for(String j : jogs) {
	    							String[] i = j.split("\\|");
	    							
	    							JogadorVars vjog = this.Gaia.Servidor.jogadoresConectados.get(i[1]);
	    							vjog.STATUS 	= "logado";
	    			            	vjog.modo 	= null;
	    			            	
	    			            	this.Gaia.Servidor.enviarParaCliente(vjog.sock, this.Gaia.Utils.json.toJson(this.Gaia.Utils.tratar("funcao=atulLogados&ACAO=inserir&NICK="+vjog.NICK+"&STATUS="+vjog.STATUS)));
	    			            	this.Gaia.Servidor.enviarParaCliente(vjog.sock, this.Gaia.Utils.json.toJson(this.Gaia.Utils.tratar("funcao=cancelarPartida")));
	    			            	
	    			            	if(!vjog.NICK.equalsIgnoreCase(jog.NICK)) {
	    			            		this.Gaia.Servidor.enviarParaCliente(vjog.sock, this.Gaia.Utils.json.toJson(this.Gaia.Utils.tratar("funcao=mensagem&MENSAGEM=O jogador "+jog.NICK+" cancelou sua participação na partida.")));
									}
	    						}
	    						
	    						this.Gaia.Servidor.enviarParaServidor(servidorUtilizado.IP + ":" + servidorUtilizado.PORT, "a=fecharServidor");
	                    	}
	                    	
	                    	this.Gaia.Servidor.propagarServidores();
	                    break;
	                    case "deslogar":
	                    	this.Gaia.Servidor.desconectarJogador(nick);
	                    break;
	                    case "chat":
	                        String msg = VARS.get("msg");
	                        
	                        if(msg.length() < 128 && msg.length() > 0) {
	                        	this.Gaia.Servidor.enviarParaTodosClientes(this.Gaia.Utils.json.toJson(this.Gaia.Utils.tratar("funcao=chatMsg&NICK="+nick+"&MENSAGEM="+msg)), null);
	                        }
	                    break;
	                    case "sincronizar":
	                    	Iterator<Entry<String, JogadorVars>> iterator = this.Gaia.Servidor.jogadoresConectados.entrySet().iterator();
	                    	
	                    	while(iterator.hasNext()) {
	                    		Entry<String, JogadorVars> entry = iterator.next();
	                    		JogadorVars jogadorInfo = entry.getValue();
	                    		this.Gaia.Servidor.enviarParaTodosClientes(this.Gaia.Utils.json.toJson(this.Gaia.Utils.tratar("funcao=atulLogados&ACAO=inserir&NICK="+jogadorInfo.NICK+"&STATUS="+jogadorInfo.STATUS)), null);
	                    	}
	                    	
	                    	this.Gaia.Servidor.propagarServidores();
	                    break;
	                    case "confirmarPronto":
	                    	PartidaVars jogPartidaInfo 				= this.Gaia.partidas.get(jog.servidorJogando).get(jog.chave);
	                    	HashMap<String, PartidaVars> partida 	= this.Gaia.partidas.get(jog.servidorJogando);
	                    	boolean comecarPartida 					= true;
	                    	String jogadorChave						= null;
	                    	PartidaVars pInfo						= null;
	                    	
	                    	if(!jogPartidaInfo.pronto) {
	                    		jogPartidaInfo.pronto = true;
	                    		
	                    		Iterator<Entry<String, PartidaVars>> iter = partida.entrySet().iterator();
	                    		while(iter.hasNext()) {
	                    			Entry<String, PartidaVars> entry 	= iter.next();
	                    			pInfo 								= entry.getValue();
	                    			JogadorVars jogador					= this.Gaia.Servidor.jogadoresConectados.get(entry.getKey());
	                    			
	                    			this.Gaia.Servidor.enviarParaCliente(jogador.sock, this.Gaia.Utils.json.toJson(this.Gaia.Utils.tratar("funcao=setarPronto&NICK="+jog.NICK+"&partidaid="+jog.servidorJogando)));
	                    			
	                				if(!pInfo.pronto) {
	                					comecarPartida = false;
	                					break;
	                				}
	                    		}
	                    		
	                    		if(comecarPartida) {
	                    			ServerVars jogServer = this.Gaia.Servidor.servidoresConectados.get(jog.servidorIP);
	                    			jogServer.STATUS = 3; // Em jogo
	                    			this.Gaia.Dao.query("UPDATE competitivo_servers SET STATUS = 3 WHERE ID = ?", new String[] {jog.servidorJogando+ ""});
	                    			
	                    			iter = partida.entrySet().iterator();
	                        		while(iter.hasNext()) {
	                        			Entry<String, PartidaVars> entry 	= iter.next();
	                        			pInfo 								= entry.getValue();
	                        			jogadorChave						= entry.getKey();
	                        			
	                        			JogadorVars jogador = this.Gaia.Servidor.jogadoresConectados.get(jogadorChave);
	                        			jogador.STATUS = "jogando"; // Em jogo
	                        			
	                        			this.Gaia.Servidor.enviarParaCliente(jogador.sock, this.Gaia.Utils.json.toJson(this.Gaia.Utils.tratar("funcao=entrarServer&IP="+jogador.servidorIP+"&SENHA="+jogServer.SENHA)));
	                        			this.Gaia.Servidor.enviarParaServidor(jog.servidorIP, "a=iniciarPartida");
	                        		}
	                    		}
	                    	}
	                    break;
	                    case "cancelarPronto":
	                    	
	                    break;
	                    case "testarServidorSAMP":
	                    	//new Thread(new TestarServidor(ip, Integer.valueOf(VARS.get("porta")), sock)).start();
	                    break;
	                }
	            } else {
	            	output = this.Gaia.Utils.json.toJson(this.Gaia.Utils.tratar("funcao=deslogar"));
	            }
	    }
		
		return output;
	}
}

package BRZLauncherServer;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import BRZLauncherServer.Variaveis.JogadorVars;
import BRZLauncherServer.Variaveis.ServerVars;

public class ServidorJava {
	// Referência da class principal
	private Gaia Gaia = null;
		
	// Variáveis locais
	public HashMap<String, JogadorVars> jogadoresConectados 	= new HashMap<String, JogadorVars>();
	public HashMap<String, ServerVars> servidoresConectados 	= new HashMap<String, ServerVars>();
	
	public ServidorJava(Gaia g) {
		this.Gaia = g;
	}
	
	private class abrirServidorOficial implements Runnable {
		private String caminho 	= null;
		private Gaia Gaia 		= null;
		
		public abrirServidorOficial(String caminho, Gaia g) {
			this.caminho 	= caminho;
			this.Gaia 		= g;
		}
		
		public void run() {
			try {
				//Process child = Runtime.getRuntime().exec("which cd "+this.caminho+"; nohup ./samp03svr &");
				//System.out.println("which cd "+this.caminho+"; nohup ./samp03svr &");
				

				Process proc = Runtime.getRuntime().exec("nohup "+caminho+"restart.sh &");
					
				this.Gaia.Dao.query("UPDATE competitivo_servers_oficiais SET ABERTO = 1 WHERE CAMINHO = ?", new String[] {this.caminho});
				System.out.println("Servidor aberto");
				proc.waitFor();
				System.out.println("Servidor fechado");
				this.Gaia.Dao.query("UPDATE competitivo_servers_oficiais SET ABERTO = 0 WHERE CAMINHO = ?", new String[] {this.caminho});
			} catch (InterruptedException | IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private String extrairServidorOficial(int porta) {
		try {
    		String serverNome 	= this.Gaia.Utils.GerarString(3, 5, false);
    		String destPath 	= "/home/brazucas/samp/Competitivo/Servers/"+serverNome+"_"+porta+"/";
    		
	    	InputStream servidorPath = BRZLauncherServer.class.getResourceAsStream("resources/servidor.zip");

	    	File unzipDestinationDirectory = new File(destPath);
	    	unzipDestinationDirectory.deleteOnExit();
	    	
	    	ZipInputStream zipFile = new ZipInputStream(servidorPath);
	    	byte[] buffer = new byte[2048];
	    	
	    	ZipEntry entry;
	    	while((entry = zipFile.getNextEntry()) != null) {
	    		String currentEntry = entry.getName();
	    		// System.getProperty("java.io.tmpdir")  "/home/brazucas/samp/Competitivo/Servers/"+serverNome
	    		File destFile = new File(destPath, currentEntry);
	    		File destinationParent = destFile.getParentFile();
	    		destinationParent.mkdirs();

	    		if(!entry.isDirectory()) {	    			
	    			FileOutputStream fos 		= new FileOutputStream(destFile);
	    			BufferedOutputStream dest 	= new BufferedOutputStream(fos, 2048);
	    			
	                int len = 0;
	                
	                while ((len = zipFile.read(buffer)) > 0) {
	                	if((new String(buffer).indexOf("7777")) != -1) {
	                		buffer = new String(buffer).replace("7777", porta+"").getBytes();
	                	}
	                	
	                	dest.write(buffer, 0, len);
	                }
	    			
	    			dest.flush();
	    			dest.close();
	    			fos.close();
	    		}
	    	}
	    	
	    	zipFile.close();
	    	
	    	// Criar script para reiniciar o servidor
	    	String txt = ""
	    			+"#!/bin/sh\n"
	    			+"log=samp.log\n"
	    			+"dat=`date`\n"
	    			+"samp=\""+destPath+"samp03svr\"\n"
	    			+"cd "+destPath+"\n"
	    			+"\n"
	    			+"while true; do\n"
	    			+"        mv "+destPath+"server_log.txt\n"
	    			+destPath+"logs/server_log.`date '+%m%d%y%H%M%S'`\n"
	    			+"        ${samp} >> $log\n"
	    			+"        sleep 2\n"
	    			+"done";

	    	File f = new File(destPath, "restart.sh");
	    	
	    	FileWriter fw = new FileWriter(f.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);
			bw.write(txt);
			bw.close();
	    	
	    	Runtime.getRuntime().exec("chmod -R 777 "+destPath);
	    	
	    	try {
	    		InetAddress addr = InetAddress.getLocalHost();            
	    		
	    		this.Gaia.Dao.query("INSERT INTO competitivo_servers_oficiais(IP, PORTA, CAMINHO) VALUES(?, ?, ?)", new String[] {addr.getHostAddress(), porta+"", destPath});
	    	} catch (UnknownHostException e) {
	    		e.printStackTrace();
	    	}
	    	
	    	return destPath;
    	} catch (IOException e) {
    		e.printStackTrace();
    		return null;
    	}
	}
	
	public boolean verificarServidoresOficiais() throws SQLException {
		ResultSet query1 = this.Gaia.Dao.query("SELECT * FROM competitivo_servers_oficiais F ORDER BY F.PORTA", null);
		ResultSet query2 = this.Gaia.Dao.query("SELECT MAX(PORTA) S_MAIOR_PORTA, COUNT(*) S_QUANTIDADE_SERVIDORES, (SELECT COUNT(*) FROM competitivo_servers_oficiais WHERE ABERTO = 0) S_NAO_ABERTOS FROM competitivo_servers_oficiais", null);
		ResultSet query3 = null;
		
		if(query2.next() && query2.getInt("S_NAO_ABERTOS") == 0 && query2.getInt("S_QUANTIDADE_SERVIDORES") < this.Gaia.quantidade_total_servidores) { // Caso todos os servidores já estejam abertos
			
			// Extrair um novo servidor e abrí-lo.
			int porta = 0;
			if(query2.getInt("S_MAIOR_PORTA") ==  0) {
				porta = 7780;
			} else {
				porta = query2.getInt("S_MAIOR_PORTA") + 1;
			}
			
			String novoServidorCaminho = this.extrairServidorOficial(porta);
			if(novoServidorCaminho != null) {
				new Thread(new abrirServidorOficial(novoServidorCaminho, this.Gaia)).start();
			}
		} else {
			while(query1.next()) {
				query3 = this.Gaia.Dao.query("SELECT COUNT(*) N FROM competitivo_servers S WHERE S.IP = ? AND S.PORTA = ? AND STATUS = 1", new String[] {query1.getString("IP"), query1.getInt("PORTA")+""});
				
				if(query2.getInt("S_NAO_ABERTOS") > 0 && query3.getInt("N") == 1) { // Abrir um servidor que já está extraído
					new Thread(new abrirServidorOficial(query1.getString("CAMINHO"), this.Gaia)).start();
				}
			}
		}
		
    	return true;
    }
	
	public void enviarParaTodosClientes(String message, String[] excluidos) {
		Iterator<Entry<String, JogadorVars>> it = this.jogadoresConectados.entrySet().iterator();
		PrintWriter writer 	= null;
		
		if(message.length() > 0) {
			while(it.hasNext()) {
				Entry<String, JogadorVars> entry = it.next();
				
				if(excluidos == null || !this.Gaia.Utils.implodeArray(excluidos, ",").contains(entry.getValue().NICK)) {
					try {
						Socket sock = entry.getValue().sock;
						
						if(!sock.isClosed() && sock != null) {
							writer = new PrintWriter(sock.getOutputStream());
							writer.println(this.Gaia.C.encrypt(message));
							writer.flush();
						}
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			}
		}
	}
	
	public void enviarParaCliente(Socket sock, String message) {
		PrintWriter writer 	= null;
		
		if(message.length() > 0) {
			try {
				if(sock.isConnected()) {
					writer = new PrintWriter(sock.getOutputStream());
					writer.println(this.Gaia.C.encrypt(message));
					writer.flush();
					
					System.out.println("[" + this.Gaia.Utils.dataHora() + "] Comando enviado para "+sock.getInetAddress().getHostAddress()+": "+message);
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}
	
	public void enviarParaServidor(String IP, String message) {
		ServerVars sv 		= this.servidoresConectados.get(IP);
		Socket sock			= sv.sock;
		PrintWriter writer	= null;
		
		if(message.length() > 0) {
			try {
				if(sock.isConnected()) {
					writer = new PrintWriter(sock.getOutputStream());
					writer.println(this.Gaia.C.encrypt(message));
					writer.flush();
					
					System.out.println("[" + this.Gaia.Utils.dataHora() + "] Comando enviado para "+sock.getInetAddress().getHostAddress()+": "+message);
				}
			
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}
	
	public void fecharTodasConexoes() {
		Iterator<Socket> it = this.Gaia.clientOutputStreams.iterator();
		while(it.hasNext()) {
			try {
				it.next().close();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}
	
	public void desconectarServidor(String ip) {
		if(this.servidoresConectados.get(ip) != null) {
			ServerVars serverInfo = this.servidoresConectados.get(ip);
			
			if(serverInfo.STATUS != 1) {
				JogadorVars jog;
				
				String[] jogs = serverInfo.JOGADORES_LISTA.split(",");
				
				for(String j : jogs) {
					String[] i = j.split("\\|");
					
					jog = this.jogadoresConectados.get(i[1]);
					
					if(jog != null) {
						jog.STATUS 	= "logado";
		            	jog.modo 	= null;
		            	
						enviarParaCliente(jog.sock, this.Gaia.Utils.json.toJson(this.Gaia.Utils.tratar("funcao=atulLogados&ACAO=inserir&NICK="+jog.NICK+"&STATUS="+jog.STATUS)));
						enviarParaCliente(jog.sock, this.Gaia.Utils.json.toJson(this.Gaia.Utils.tratar("funcao=cancelarPartida")));
						enviarParaCliente(jog.sock, this.Gaia.Utils.json.toJson(this.Gaia.Utils.tratar("funcao=mensagem&MENSAGEM=Ocorreu um erro inesperado.\n\nDescrição: A conexão com o servidor de jogo foi perdida.")));

					}
				}
				
				this.propagarServidores();
			}
			
			this.Gaia.Dao.query("DELETE FROM competitivo_servers WHERE IP=? AND PORTA=?", new String[] {serverInfo.IP, serverInfo.PORT+""});
			 this.servidoresConectados.remove(ip);
			 
			 this.propagarServidores();
		}
	}
	
	public void propagarServidores() {
		int servidoresDisponiveis 	= 0;
		int servidoresTotais		= 0;
		Iterator<Entry<String, ServerVars>> o = this.servidoresConectados.entrySet().iterator();
		while(o.hasNext()) {
			Entry<String, ServerVars> entry = o.next();
			
			if(entry.getValue().STATUS == 1) {
				++servidoresDisponiveis;
			}
			
			++servidoresTotais;
		}
		
		if(this.Gaia.Utils == null) {
			System.out.println("teste");
		}

		this.enviarParaTodosClientes(this.Gaia.Utils.json.toJson(this.Gaia.Utils.tratar("funcao=atulServers&disponiveis="+servidoresDisponiveis+"&totais="+servidoresTotais)), null);
	}
	
	public void desconectarJogador(String chave) throws SQLException, IOException {
		JogadorVars jog = this.jogadoresConectados.get(chave);
				
		if(jog != null && jog.sock.isConnected()) {
		    this.Gaia.Dao.query("UPDATE competitivo_contas SET LOGADO=0, CHAVE_AUTH='' WHERE NICK=?", new String[] {jog.NICK});
		    this.Gaia.Dao.query("DELETE FROM competitivo_fila WHERE NICK=?", new String[] {jog.NICK});
		    this.Gaia.Dao.query("DELETE FROM competitivo_atualizacoes WHERE NICK=?", new String[] {jog.NICK});
		    
		    enviarParaTodosClientes(this.Gaia.Utils.json.toJson(this.Gaia.Utils.tratar("funcao=atulLogados&ACAO=remover&NICK="+jog.NICK)), null);
		    enviarParaTodosClientes(this.Gaia.Utils.json.toJson(this.Gaia.Utils.tratar("funcao=chatMsg&NICK="+jog.NICK+"&TIPO=3&MENSAGEM="+this.Gaia.Utils.encodeURIComponent(jog.NICK+" saiu do chat."))), null);
		    
		    this.jogadoresConectados.remove(chave);
		}
	}
}

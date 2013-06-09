package BRZLauncherServer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import BRZLauncherServer.Variaveis.JogadorVars;
import BRZLauncherServer.Variaveis.ServerVars;

public class Servidor extends Gaia {
	// Referência da class principal
	private Gaia Gaia = null;
		
	// Variáveis locais
	public HashMap<String, JogadorVars> jogadoresConectados 	= new HashMap<String, JogadorVars>();
	public HashMap<String, ServerVars> servidoresConectados 	= new HashMap<String, ServerVars>();
	
	public Servidor(Gaia g) {
		this.Gaia = g;
	}
	
	public boolean abrirNovoServidor() {
    	try {
	    	System.out.println("Extraindo servidor....");
	    	InputStream servidorPath = BRZLauncherServer.class.getResourceAsStream("resources/Servidor/servidor.zip");

	    	File unzipDestinationDirectory = new File(this.Gaia.Utils.tempFolder);
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
	    	System.out.println("Servidor extraído.");
	    	//portforward();
	    	//JOptionPane.showMessageDialog(BRZLauncher.frame, "Servidor extraído.");
    	} catch (IOException e) {
    		e.printStackTrace();
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
	
	public void enviarParaCliente(Socket sock, String message) {
		PrintWriter writer 	= null;
		
		if(message.length() > 0) {
			try {
				if(sock.isConnected()) {
					writer = new PrintWriter(sock.getOutputStream());
					writer.println(message);
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
					writer.println(message);
					writer.flush();
					
					System.out.println("[" + this.Gaia.Utils.dataHora() + "] Comando enviado para "+sock.getInetAddress().getHostAddress()+": "+message);
				}
			
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}
	
	public void fecharTodasConexoes() {
		Iterator<Socket> it = clientOutputStreams.iterator();
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
					jog = this.jogadoresConectados.get(j);
					
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
	
	public void desconectarJogador(String nick) throws SQLException, IOException {
		if(this.jogadoresConectados.get(nick) != null) {
		    Dao.query("UPDATE competitivo_contas SET LOGADO=0, CHAVE_AUTH='' WHERE NICK=?", new String[] {nick});
		    Dao.query("DELETE FROM competitivo_fila WHERE NICK=?", new String[] {nick});
		    Dao.query("DELETE FROM competitivo_atualizacoes WHERE NICK=?", new String[] {nick});
		    
		    enviarParaTodosClientes(this.Gaia.Utils.json.toJson(this.Gaia.Utils.tratar("funcao=atulLogados&ACAO=remover&NICK="+nick)), null);
		    enviarParaTodosClientes(this.Gaia.Utils.json.toJson(this.Gaia.Utils.tratar("funcao=chatMsg&NICK="+nick+"&TIPO=3&MENSAGEM="+this.Gaia.Utils.encodeURIComponent(nick+" saiu do chat."))), null);
		    
		    this.jogadoresConectados.remove(nick);
		}
	}
}

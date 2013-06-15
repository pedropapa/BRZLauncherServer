package BRZLauncherServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.SQLException;
import java.util.HashMap;

import BRZLauncherServer.Variaveis.JogadorVars;

public class ClienteThread implements Runnable {
	BufferedReader 	reader 	= null;
	Socket 			sock 	= null;
	PrintWriter		writer 	= null;
	Gaia Gaia 				= null;
	
	public ClienteThread(Gaia g, Socket clientSocket) {
		this.Gaia = g;
		
		try {
			sock 						= clientSocket;
			InputStreamReader isReader 	= new InputStreamReader(sock.getInputStream());
			reader 						= new BufferedReader(isReader);
			writer 						= new PrintWriter(sock.getOutputStream());
		} catch(Exception ex) {
			ex.printStackTrace();
		}
	}
	
	public void run() {
		String resposta 			= "";
		String ip 					= sock.getInetAddress().getHostAddress();
		String nick					= null;
		String jogChave				= null;
		int port					= 0;
		
		try {
			while((resposta = this.Gaia.C.decrypt(reader.readLine())) != null) {
				System.out.println("[" + this.Gaia.Utils.dataHora() + "] Comando recebido de "+ip+": "+resposta);
				HashMap<String, String> VARS 	= this.Gaia.Utils.tratar(resposta);
				String 	output					= "";
				
				if(VARS.get("t") != null && VARS.get("a") != null) {
					switch(VARS.get("t")) {
						case "server":
							// Comandos  enviados pelos Servidores SA-MP
							output = this.Gaia.ServidorSamp.filtrar(VARS, sock);
						break;
						/** *********************************************************************************************************************************************** **/
						case "cliente":
							// Comandos enviado pelos Clientes Java
				            output = this.Gaia.Cliente.filtrar(VARS, sock);
				            
				            JogadorVars jog = this.Gaia.Servidor.jogadoresConectados.get(VARS.get("c"));
				            if(jog != null) {
				            	nick 		= jog.NICK;
				            	jogChave 	= jog.chave;
				            }
			            break;
					}
				}
				
				if(output != null && output.length() > 0) {
					writer.println(this.Gaia.C.encrypt(output));
					writer.flush();
					
					System.out.println("[" + this.Gaia.Utils.dataHora() + "] Comando enviado para "+ip+": "+output);
				}
			}
		} catch (Exception e) {
			//e.printStackTrace()
			if(nick != null) {
				System.out.println("Conexão perdida com o cliente "+ip+" ("+nick+")");
				
				try {
					this.Gaia.Servidor.desconectarJogador(jogChave);
				} catch (SQLException | IOException e1) {
					System.out.println("Não foi possível deslogar o jogador "+nick+".");
				}
			} else if(this.Gaia.Servidor.servidoresConectados.get(ip+":"+port) != null) {
				System.out.println("Conexão perdida com o servidor "+ip+":"+port);
				this.Gaia.Servidor.desconectarServidor(ip+":"+port);
			}
			
			try {
				sock.close();
			} catch (IOException e1) {

			}
		}
	}
}
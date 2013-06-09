package BRZLauncherServer.Variaveis;

import java.io.IOException;
import java.net.Socket;

public class ServerVars {
	public String 	IP 						= null;
	public int 		PORT 					= 0;
	
	/**
	 * Status do servidor
	 * 0 - Não conectado
	 * 1 - Disponível
	 * 2 - Em jogo
	 */
	public int 		STATUS					= 0;
	public int 		JOGADORES_CONECTADOS 	= 0;
	public Socket 	sock					= null;
	public String 	JOGADORES_LISTA			= null;
	public String 	SENHA					= null;
	public String 	chave					= null;
	
	public ServerVars(String ip, int port, int status, int jogadoresConectados, Socket socket, String senha, String CHAVE) throws IOException {
		IP 						= ip;
		PORT 					= port;
		STATUS				 	= status;
		JOGADORES_CONECTADOS 	= jogadoresConectados;
		sock					= socket;
		SENHA					= senha;
		chave					= CHAVE;
	}
}
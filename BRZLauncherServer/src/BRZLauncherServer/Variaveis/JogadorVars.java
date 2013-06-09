package BRZLauncherServer.Variaveis;

import java.io.IOException;
import java.net.Socket;
import java.sql.ResultSet;
import java.sql.SQLException;

public class JogadorVars {
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
	 * Situação do jogador enquanto a partida em que joga ainda está sendo disputada.
	 * 0 - Nenhuma situação (a partida não começou)
	 * 1 - Normal (está jogando)
	 * 2 - Desconectado do servidor (saiu por qualquer razão do servidor enquanto a partida ainda está sendo disputada)
	 */
	public int situacao			= 0;
	
	public JogadorVars(String nick, String status, Socket socket, String CHAVE, ResultSet competitivo_dados) throws IOException {
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
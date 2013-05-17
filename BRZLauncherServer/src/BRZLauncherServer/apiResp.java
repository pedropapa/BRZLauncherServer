package BRZLauncherServer;

import java.util.List;

public class apiResp {
	public int 		status		= 0;
	public String 	target		= null;
	public String 	html 		= null;
	public int 		CODIGO		= 0;
	public String 	funcao		= null;
	public String 	chave 		= null;
	public Object 	infos 		= null;
	
	public static class infosResp {
		public int logados 										= 0;
		public List<servidoresResp> 		servidores 			= null;
		public List<atualizacoesResp> 		atualizacoes 		= null;
		public List<jogadoresLogadosResp> 	jogadoresLogados	= null;
		public List<chatMensagensResp>		chatMensagens		= null;
	}
	
	public static class servidoresResp {
		public String IP 	= null;
		public String Senha = null;
	}
	
	public static class atualizacoesResp {
		public String 	NICK 		= null;
		public String 	ELEMENTO 	= null;
		public String 	VALOR		= null;
		public String 	CALLBACK	= null;
		public String 	EXTRA1 		= null;
		public String 	EXTRA2 		= null;
		public String 	EXTRA3 		= null;
		public String 	EXTRA4 		= null;
		public String 	EXTRA5 		= null;
		public String 	TIMESTAMP 	= null;
	}
	
	public static class jogadoresLogadosResp {
		public String NICK 		= null;
		public String STATUS 	= null;
	}
	
	public static class chatMensagensResp {
		public String 	NICK 			= null;
		public String 	MENSAGEM  		= null;
		public long		UNIX_TIMESTAMP 	= 0;
		public int 		TIPO			= 0;
	}
}

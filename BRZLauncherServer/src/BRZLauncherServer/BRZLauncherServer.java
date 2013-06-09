package BRZLauncherServer;

import java.io.IOException;

public class BRZLauncherServer {
	public static void main(String[] args) throws IOException {
		Gaia g = new Gaia();
		
		g.Dao 			= new Mysql(g);
		g.Api 			= new Api(g);
		g.Servidor 		= new Servidor(g);
		g.Utils 		= new Utils(g);
		g.Cliente		= new ClienteComandos(g);
		g.ServidorSamp 	= new ServidorSampComandos(g);
		
		g.init();
	}
}
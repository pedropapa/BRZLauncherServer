package BRZLauncherServer;

public class BRZLauncherServer {	
	public static void main(String[] args) throws Exception {
		Gaia g = new Gaia();
		
		g.Dao 			= new Mysql(g);
		g.Api 			= new Api(g);
		g.Servidor 		= new ServidorJava(g);
		g.Utils 		= new Utils(g);
		g.C				= g.Utils.new AESencrp();
		g.Cliente		= new ClienteComandos(g);
		g.ServidorSamp 	= new ServidorSampComandos(g);
		
		g.init();
	}
}
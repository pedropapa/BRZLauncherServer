package BRZLauncherServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

public class Api extends Gaia {
	// Referência da class principal
	private Gaia Gaia 				= null;
		
	public Api(Gaia g) throws IOException {
		this.Gaia = g;
	}

	private String brzLauncherUrl											= "http://samp.brazucas-server.com/BRZLauncher.php";
	private String brzUrlAPI												= "http://samp.brazucas-server.com/api.php?";
	
	public String Request(String options) {
		String brzUrlAPI 	= "http://samp.brazucas-server.com/api.php?";
		String resposta		= "";
		
		try {
        	URL oracle 			= new URL(brzUrlAPI+options);
            URLConnection yc 	= oracle.openConnection();
            BufferedReader in 	= new BufferedReader(new InputStreamReader(yc.getInputStream()));
            
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                resposta += inputLine;
            }
            
            in.close();
    	} catch (IOException E) {
    		E.printStackTrace();
		}
		
		return resposta;
	}
	
	public void atualizarMasterIP() throws IOException {
		System.out.println("Atualizando IP do servidor principal na API...");
		((new URL(this.brzUrlAPI+"a=updateMasterIp")).openStream()).close();
	}
}

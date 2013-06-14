package BRZLauncherServer;

import java.io.IOException;
import java.net.Socket;

public class TestarServidor extends ServidorJava implements Runnable {
	public TestarServidor(Gaia g) {
		super(g);
	}
	
//	public TestarServidor(String IP, Integer PORTA, Socket SOCK) throws IOException {
//		ip 		= IP;
//		porta 	= PORTA;
//		sock 	= SOCK;
//	}

	public String ip 	= null;
	public int porta 	= 0;
	public Socket sock 	= null;
	

	
	public void run() {
//		try {	        
//        	new Socket(ip, Integer.valueOf(porta));
//        	enviarParaCliente(sock, Utils.json.toJson(Utils.tratar("funcao=testarServidorSAMPSucesso")));
//        } catch (IOException e) {
//        	enviarParaCliente(sock, Utils.json.toJson(Utils.tratar("funcao=testarServidorSAMPFalha")));
//        }
	}
}

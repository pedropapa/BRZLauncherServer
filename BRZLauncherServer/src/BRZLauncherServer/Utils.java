package BRZLauncherServer;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.Key;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import sun.misc.*;

import com.google.gson.Gson;

public class Utils extends Gaia {
	// Referência da class principal
	private Gaia Gaia = null;
		
	public Utils(Gaia g) throws IOException {
		this.Gaia = g;
	}

	// Variáveis locais
	public Gson json 			= new Gson();
	public String tempFolder 	= System.getProperty("java.io.tmpdir");
	public AESencrp AESencrp 	= new AESencrp();
	
	public HashMap<String, String> tratar(String parametros) {
		HashMap<String, String> GET = new HashMap<String, String>();
		
		String[] vars 	= parametros.split("&");
		String[] indVal = null;
		
		for(int i = 0; i < vars.length; ++i) {
			if(vars[i].indexOf("=") != -1) {
				indVal = vars[i].split("=");
				GET.put(indVal[0], indVal[1]);
			}
		}
		
		return GET;
	}
	
	public String dataHora() {
		return (new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(Calendar.getInstance().getTime()));
	}
	
	public String implodeArray(String[] inputArray, String glueString) {
		String output = "";
		
		if(inputArray.length > 0) {
			StringBuilder sb = new StringBuilder();
			sb.append(inputArray[0]);
			
			for(int i = 1; i < inputArray.length; ++i) {
				sb.append(glueString);
				sb.append(inputArray[i]);
			}
			
			output = sb.toString();
		}
		
		return output;
	}
	
	public String encodeURIComponent(String component)   {     
    	String result = null;      
    	
    	try {
    		result = URLEncoder.encode(component, "UTF-8")   
    			   .replaceAll("\\%28", "(")                          
    			   .replaceAll("\\%29", ")")   		
    			   .replaceAll("\\+", "%20")                          
    			   .replaceAll("\\%27", "'")   			   
    			   .replaceAll("\\%21", "!")
    			   .replaceAll("\\%7E", "~");     
    	} catch (UnsupportedEncodingException e) {       
    		result = component;     
    	}      
    	
    	return result;   
    }
	
	public String decodeURIComponent(String s) {
	    if (s == null) {
	      return null;
	    }

	    String result = null;

	    try {
	    	result = URLDecoder.decode(s, "UTF-8");
	    }

	    catch (UnsupportedEncodingException e) {
	    	result = s;  
	    }

	    return result;
	}
	
	public String GerarString(int tipo, int tamanho, boolean salt) {
		String letras     	= "abcdefghijklmnopqrstuvxz";
	    String numeros    	= "0123456789";
	    String output     	= "";
	    String chars		= "";
	    
	    switch(tipo) {
	        case 1:
	            chars = letras;
	        break;
	        case 2:
	            chars = numeros;
	        break;
	        case 3:
	            chars = letras + numeros;
	        break;
	    }
	    
	    for(int i = 0; i < tamanho; ++i)
	        output += chars.charAt(new Random().nextInt(chars.length()));
	    
	    // Salt
	    return (salt)?"BRz_kuZn3tS0v?"+output:output;
	}
	
	public String GerarString(int tipo, int tamanho) {
		return GerarString(tipo, tamanho, true);
	}
	
	public class AESencrp {
		private final String ALGO = "AES";
	    private final byte[] keyValue = new byte[] {'p','@','P','@','D','0','p','0','L','1','s','0','0','0','1','9'};

		public String encrypt(String Data) throws Exception {
		        Key key = generateKey();
		        Cipher c = Cipher.getInstance(ALGO);
		        c.init(Cipher.ENCRYPT_MODE, key);
		        byte[] encVal = c.doFinal(Data.getBytes());
		        String encryptedValue = new BASE64Encoder().encode(encVal);
		        return encryptedValue;
		    }
	
		    public String decrypt(String encryptedData) throws Exception {
		        Key key = generateKey();
		        Cipher c = Cipher.getInstance(ALGO);
		        c.init(Cipher.DECRYPT_MODE, key);
		        byte[] decordedValue = new BASE64Decoder().decodeBuffer(encryptedData);
		        byte[] decValue = c.doFinal(decordedValue);
		        String decryptedValue = new String(decValue);
		        return decryptedValue;
		    }
		    private Key generateKey() throws Exception {
		        Key key = new SecretKeySpec(keyValue, ALGO);
		        return key;
		}
	}
}

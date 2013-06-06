package sd.wikitest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.xml.bind.annotation.adapters.HexBinaryAdapter;

public class Utils {

	
	
	/**
	 * Returns an md5 with upper case letters. 
	 * 
	 * @param input
	 * @return
	 */
	public static String md5( String input ){
		if( input == null ) return "O_O"; 
		
        MessageDigest m;
		try {
			m = MessageDigest.getInstance("MD5");
			byte[] out = m.digest(input.getBytes());
			
			//return new String( out );
			return (new HexBinaryAdapter()).marshal(out);
		}
		catch (NoSuchAlgorithmException e) {
			throw new RuntimeException( "whatever, as if i couldn't spell md5 right" ); 
		}
	}
	
	public static String urlEncode( String str ){
		try {
			return URLEncoder.encode( str, "UTF-8" );
		} catch (UnsupportedEncodingException e) {
			return "WTOOTOTFOFO how would utf-8 not exist? this is just silly!!!"; 
		} 
	}
	
	public static String toWikiPathname( String pageName ){
		if( pageName == null ) return null; 
		
		pageName = pageName.replace( ' ', '_' ); 
		if( pageName.length() >= 1 )
			pageName = pageName.substring( 0, 1 ).toUpperCase() + pageName.substring( 1 );
		
		try {
			return URLDecoder.decode( pageName.replaceAll( "\\+", "%2B" ), "UTF-8" );
		} catch (UnsupportedEncodingException e) {
			return pageName; 
		} 
	}
	
	
	public static void download( String url, File dest ) throws MalformedURLException, IOException{
		try( InputStream in = new URL( url ).openStream(); 
			FileOutputStream imageOut = new FileOutputStream( dest ) ){ 
			byte data[] = new byte[8096]; 
			int len = 0; 
			while( ( len = in.read( data ) ) > 0 ){
				imageOut.write( data, 0, len ); 
			}
		}
	}
}

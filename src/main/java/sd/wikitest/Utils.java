package sd.wikitest;

import info.bliki.wiki.filter.TemplateParser;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.annotation.adapters.HexBinaryAdapter;

/**
 * Some utils. 
 * General wikipedia parsing tricks: 
 * - there's weird whitespace sometimes, you can typically catch those with  
 *   \\p{C} in your regex 
 * 
 * @author hansi
 *
 */
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
	
	
	/**
	 * finds all instances of a template and returns all of its parameters
	 * e.g. if you pass in
	 * wikiText = 
	 *   {{Taxobox name=Aardwolf|ordo=[[Carnivora]]}}
	 *   {{Taxobox name=Northern cavefish|status=VU}}
	 * templateName = 
	 *   Taxobox
	 *  
	 * returns a neat list of templaterefs for you 
	 * 
	 * @param wikiText the wiki text. you must manually strip comments before passing this in here
	 * @param templateName the template you're looking for, case sensitive!  
	 * @return
	 */
	public static List<TemplateInfo> findTemplates( String wikiText, String templateName ){
		List<TemplateInfo> templates = new LinkedList<>(); 
		int start = 0; 
		char[] chars = wikiText.toCharArray();
		
		while( ( start = wikiText.indexOf( "{{" + templateName, start ) ) >= 0 ){
			int end = TemplateParser.findNestedEndSingle( chars, '{', '}', start + 1 );
			if( end > start ){
				templates.add( new TemplateInfo( wikiText, start, end ) );
				start = end; 
			}
			
			// move along, a bit at least
			start ++; 
		}
		
		return templates; 

	}
	
	
	/**
	 * A thing that parses a template
	 * You must only figure out where that template is. 
	 * @author hansi
	 *
	 */
	public static class TemplateInfo{
		// regex to find name=value pairs, filtering out some weird eventual spacings
		public final static Pattern nameValuePattern = Pattern.compile( "^[\\s\\p{C}]*([\\w_]+)[\\s\\p{C}]*\\=(.*)$", Pattern.MULTILINE | Pattern.DOTALL );
		
		// index of the first opening { and the last closing }
		public final int startIndex;
		public final int endIndex;
		
		// name of the template, e.g. Taxobox
		public final String name; 
		
		// snippet of wiki text that describes this template
		// this includes {{Templatename...}}
		public final String templateText; 
		
		// and the stripped wiki text, this is all the template arguments 
		// without {{Templatename and without the final }}
		public final String innerTemplateText; 
		
		// all named params
		public final HashMap<String,String> namedParams = new HashMap<>(); 
		
		// all params in order
		public final List<String> params; 
		
		/**
		 * Parse a template. 
		 * The string passed in must begin with {{ and end with }}  
		 *  
		 * @param wikiText
		 */
		public TemplateInfo( String wikiText ){
			this( wikiText, 0, wikiText.length() ); 
		}
		
		/**
		 * @param startIndex
		 * @param endIndex
		 * @param wikiChars char array of the wikipage. basically you can use yourWikiText.toCharArray, but if you want to reuse this more of you'll find this much more efficient. 
		 */
		public TemplateInfo( String wikiText, int startIndex, int endIndex ){
			this.startIndex = startIndex;
			this.endIndex = endIndex;
			this.templateText = wikiText.substring( startIndex, endIndex );  
			char[] wikiChars = templateText.toCharArray(); 
			
			Object[] paramMess = TemplateParser.createParameterMap( wikiChars, 2, wikiChars.length - 2 ); 
			this.name = (String) paramMess[1];  
			this.params = (ArrayList<String>) paramMess[0];
			this.innerTemplateText = wikiText.substring( 2 + name.length(), wikiText.length() - 2 ); 
			
			for( String p : params ){
				Matcher matcher = nameValuePattern.matcher( p ); 
				if( matcher.matches() ){
					namedParams.put( matcher.group( 1 ), matcher.group( 2 ).trim() ); 
				}
			}
		}
	}
}

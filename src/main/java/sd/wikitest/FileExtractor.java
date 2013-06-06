package sd.wikitest;


import info.bliki.wiki.dump.IArticleFilter;
import info.bliki.wiki.dump.Siteinfo;
import info.bliki.wiki.dump.WikiArticle;
import info.bliki.wiki.dump.WikiXMLParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Downloads images for the the pruned-names.csv file. 
 * you'll need a species wiki dump specieswiki-20130508-pages-articles-multistream.xml.bz2
 * 
 * Results are written into ./images/*, 
 * existing images are skipped so you can safely stop and run the script, 
 * but be sure to always delete the last image when after stopping as it might be  
 * downloaded only half way.
 * 
 * @author hansi
 *
 */
public class FileExtractor {

	// finds file links 
	// like [[File:Hylobates_concolor2.jpg|thumb|220px|''[[Nomascus]]'']] 
	static Pattern filePattern = Pattern.compile( "\\[\\[(?:File|Image):\\s*([^|\\]\\p{C}]+)", Pattern.CASE_INSENSITIVE );
	static int images = 0; 
	static int speciesWithImages = 0; 

	public static void main(String[] args) throws IOException, SAXException {
		System.out.println( "Reading pruned-names.csv ... " ); 
		final HashSet<String> articles = new HashSet<String>();
		BufferedReader in = new BufferedReader( new InputStreamReader( new FileInputStream( "pruned-names.csv" ) ) );
		String line; 
		while( ( line = in.readLine() ) != null ){
			articles.add( line.split( "†" )[0] ); 
		}
		System.out.println( "Loaded " + articles.size() + " articles" );
		
		
		System.out.println( "Searching for matching images ..." ); 
		String bz2Filename = "/Users/hansi/Downloads/wiki/specieswiki-20130508-pages-articles-multistream.xml.bz2";
    	BZip2CompressorInputStream zip = new BZip2CompressorInputStream( new FileInputStream( bz2Filename ), true ); 
    	Reader reader = new InputStreamReader( zip );
    	
		final PrintWriter out = new PrintWriter( "images.csv" ); 
    	
    	IArticleFilter handler = new IArticleFilter() {
			public void process( WikiArticle article, Siteinfo site ) throws SAXException {
				String title = article.getTitle(); 
				String text = article.getText(); 

				if( title != null && articles.contains( title.toLowerCase() ) ){
					Matcher matcher = filePattern.matcher( text );
					int start = 0; 
					while( matcher.find( start ) ){
						if( start == 0 ) speciesWithImages ++; 
						images ++; 
						
						String imageName = Utils.toWikiPathname( matcher.group( 1 ) );
						String md5 = Utils.md5( imageName ).toLowerCase(); 
						String url = 
								"http://upload.wikimedia.org/wikipedia/commons/" + 
								md5.substring( 0, 1 ) + "/" + 
								md5.substring( 0, 2 ) + "/" + 
								Utils.urlEncode( imageName ); 
						
						File dest = new File( "images/" + imageName ); 
						if( !dest.exists() || dest.length() == 0 ){
							System.out.println( images + "Downloading " + title.toLowerCase() + "†" + matcher.group( 1 ) + "†" + url );
							
							try{
								Utils.download( url, dest );
							}
							catch( FileNotFoundException fnf ){
								System.out.println( "file not found, lets hope its a redirect! " ); 
								try {
									String redirUrl = "http://commons.wikimedia.org/w/api.php?action=query&prop=imageinfo&iiprop=url&titles=File:" + imageName + "&continue=&format=xml&redirects"; 
									Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse( redirUrl );
									XPath xpath = XPathFactory.newInstance().newXPath();
									String redirTarget = ((String) xpath.evaluate("//ii/@url", doc, XPathConstants.STRING));
									if( redirTarget.equals( "" ) ){
										Thread.sleep( 3000 ); 
										System.err.println( "tried redirect: " + redirUrl ); 
										System.err.println( "file missing/deleted" ); 
									}
									else{
										Utils.download( redirTarget, dest );
									}
								} catch (IOException | ParserConfigurationException | XPathExpressionException | InterruptedException e) {
									System.out.println( "Still no luck, oh well..." ); 
									e.printStackTrace();
								}
							}
							catch (Exception e) {
								e.printStackTrace();
								dest.delete(); 
							}
						}
						else{
							System.out.println( "Skip " + title ); 
						}
						
						
						if( dest.exists() && dest.length() == 0 ){
							out.println( title.toLowerCase() + "†" + imageName );
						}

						start = matcher.end();
						
					}
				}
			}
    	};
    	
		WikiXMLParser wxp = new WikiXMLParser(reader, handler);
		wxp.parse();
		
		out.close(); 
		
		System.out.println( "Images: " + images );
		System.out.println( "Images: " + speciesWithImages ); 
	}
}
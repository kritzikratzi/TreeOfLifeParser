package sd.wikitest;

import info.bliki.wiki.dump.IArticleFilter;
import info.bliki.wiki.dump.Siteinfo;
import info.bliki.wiki.dump.WikiArticle;
import info.bliki.wiki.dump.WikiXMLParser;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.xml.sax.SAXException;

import sd.wikitest.Utils.TemplateInfo;

/**
 * a measly attempt to match up 
 * wikispecies articles with wikipedia articles. 
 */
public class EnWiki 
{
	static boolean shout = false;
	
	static int max = 50000000; 
	static int count = 0;  
	static Hashtable<String, String> rel = new Hashtable<String, String>();
	
	// many sites, for some reason, have use the main page template. we simply remove that.
	static Pattern mainLinkRemover = Pattern.compile( "\\{\\{Main Page\\}\\}", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE );
	
	// same with images
	static Pattern imageRemover = Pattern.compile( "\\{\\{Image\\|[^}]+\\}\\}", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE );
	
	// Taxo template usage comes in one of three possible varieties: 
	//   {{template-name}}
	//   {{Taxonav|template-name}}
	//   {{Taxonav|template-name|display-name}}
	// we only care about the template-name. 
	static Pattern templateExtractor = Pattern.compile( "(?:Taxonav\\||)([^\\|}]+)(?:[^}]+|)", Pattern.CASE_INSENSITIVE ); 
	
	
    public static void main( String[] args ) throws UnsupportedEncodingException, FileNotFoundException, IOException, SAXException, IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException
    {
    	String bz2Filename = "/Users/hansi/Downloads/wiki/enwiki-20130403-pages-articles-multistream.xml.bz2";
    	BZip2CompressorInputStream zip = new BZip2CompressorInputStream( new FileInputStream( bz2Filename ), true ); 
    	Reader reader = new InputStreamReader( zip );
    	
    	
    	IArticleFilter handler = new IArticleFilter() {
			public void process( WikiArticle article, Siteinfo site ) throws SAXException {
				if( article.getText() == null ){
					System.err.println( "Empty article: " + article.getTitle() );
					return; 
				}
				
				String title = article.getTitle().toLowerCase(); 
				
				// remove all images from the text before doing anything. they sortof annoy us! 
//				String text = imageRemover.matcher( article.getText().toLowerCase() ).replaceAll( "" );
				String text = article.getText(); 
				
				String x = "tNamespace().equa( \"\" ) ls( \"\" ) && text.indexOf( \"{{Taxobox\" ) > 0 ){\n" + 
						"					System.out.println( \"ANIMAL: \" + title ); \n" + 
						"					// do we have taxo info?\n" + 
						"					List<TemplateInfo> templates = Utils.findTemplates( text, \"Taxobox\" );\n" + 
						"					for( TemplateInfo info : templates ){\n" + 
						"						System.out.println( \"Template: \" + info.namedParams.get( \"name\" ) );\n" + 
						"						System"; 
				if( article.getNamespace().equals( "" ) && text.indexOf( "{{Taxobox" ) > 0 ){
					System.out.println( "ANIMAL: " + title ); 
					// do we have taxo info?
					List<TemplateInfo> templates = Utils.findTemplates( text, "Taxobox" );
					for( TemplateInfo info : templates ){
						System.out.println( "Template: " + info.namedParams.get( "name" ) );
						System.out.println( "Fossil Range: " + info.namedParams.get( "fossil_range" ) ); 
					}
					
				}
				
				count ++;
				if( count % 1000 == 0 )
					System.out.println( count ); 
			}
		};
		
		WikiXMLParser wxp = new WikiXMLParser(reader, handler);
		wxp.parse();
		
		
		
		count = 0; 
    }
    
    
    static void addRelToTemplate( String a, String b ){
    	Matcher matcher = templateExtractor.matcher( b ); 
    	if( matcher.find() ){
    		rel.put( a, "template:" + matcher.group(1).trim() );
    		if( shout ) System.out.println( a + " -> template:" + matcher.group(1) );
    	}
    	else{
    		System.err.println( "Template name not parsable: " + b ); 
    	}
    }
    
    static void addRelToArticle( String a, String b ){
    	rel.put( a,  b.trim() ); 
		if( shout ) System.out.println( a + " -> " + b ); 
    }
    
}

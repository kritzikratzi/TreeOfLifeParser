package sd.wikitest;

import info.bliki.wiki.dump.IArticleFilter;
import info.bliki.wiki.dump.Siteinfo;
import info.bliki.wiki.dump.WikiArticle;
import info.bliki.wiki.dump.WikiXMLParser;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.xml.sax.SAXException;

/**
 * Extracts a taxo tree from a wikispecies dump
 * You'll need specieswiki-20130508-pages-articles-multistream.xml.bz2 
 * 
 * Two files are generated: 
 * - pruned-names.csv (contains names) 
 * - pruned-nums.csv (contains ids, kinda useless)
 * 
 * In each line you'll have child†parent
 * 
 * 
 * The root is template:hansi:root, 
 * some species are inserted to root the tree (eukaryota, prokaryota)  
 * but i'm not sure biologists would agree... 
 */
public class PrunedCSV 
{
	static boolean shout = false;
	
	static int count = 0;  
	static Hashtable<String, String> rel = new Hashtable<String, String>();
	static Hashtable<String, String> redirects = new Hashtable<String, String>(); 
	
	// if there's a taxo headline the wikipage usually looks like this: 
	//   ==Taxonavigation==
	//   {{parent}}
	static Pattern articleTaxo = Pattern.compile( "^==\\s*Taxonavigation\\s*==[^=]*?\\{\\{([^\\}]+)\\}\\}", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE );
	
	// sometimes this doesn't exist, and we then start looking for links to other pages, like 
	//   Genus: [[Astragalus]]
	// this sortof works, but the problem is that sometimes i just pick the first such occurence, 
	// so for instance if the page Astragalus contains
	//   Order: [[Fabales]]
	//   Family: [[Fabaceae]]
	// it will be linked to fabales, not the much more accurate fabaceae. 
	// definitely possible to fix this, not a priority yet. 
	static Pattern articleAltTaxo = Pattern.compile( "(?:Life|Domain|Kingdom|Phylum|Class|Order|Family|Genus|Species)[:]? \\[\\[([^\\]]+)\\]\\]", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE );
	
	// lets learn about redirects :) 
	// currently redirects are inserted as links. for instance the page Cephalotaxaceae 
	//   #redirect [[Taxaceae]]
	// will create a link  Cephalotaxaceae -> Taxaceae 
	static Pattern redirect = Pattern.compile( "#REDIRECT \\[\\[([^\\]]+)\\]\\]", Pattern.CASE_INSENSITIVE );
	
	// templates are mostly straight forward, they simply have a link to the parent template int the first line. 
	// however, some have this {{displaytitle}} tag at the beginning which we want to skip. other than that 
	// your usual taxo template looks like 
	//   {{parent}}
	// however, sometimes there's is some crap before, like on Template:Cryptoniscoidea
	//   Subordo: {{Cymothoida}}
	//   Superfamilia: [[Cryptoniscoidea]] 
	// this is great because it has not only some weird text before the template link to the parent, 
	// but it also has a cyclic reference. wiki ftw! 
	static Pattern templateTaxo = Pattern.compile( "^(?:\\{\\{DISPLAYTITLE:''\\{\\{FULLPAGENAME\\}\\}''\\}\\}|)\\s*(?:\\w+[:]?\\s+|)\\{\\{([^\\}]+)\\}\\}", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
	
	// ... or not. because some list their taxonomy in a wikitable, which will appear as 
	//  {{wikitable 
	//   .... 
	//  |{{parent}}
	//  }}
	// in a measly attempt this will match |{{anything}} if the above pattern failed. 
	static Pattern templateAltTaxo = Pattern.compile( "^\\s*\\|\\s*\\{\\{([^\\}]+)\\}\\}", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE ); 
	
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
    	String bz2Filename = "/Users/hansi/Downloads/wiki/specieswiki-20130508-pages-articles-multistream.xml.bz2";
    	BZip2CompressorInputStream zip = new BZip2CompressorInputStream( new FileInputStream( bz2Filename ), true ); 
    	Reader reader = new InputStreamReader( zip );
    	
		/////////////////////////////////////////////////////
		/////////////////////////////////////////////////////
		/////////////////////////////////////////////////////
    	// Step 1: manualy create the root of the tree
    	// i'm not quite certain about this bit, i couldn't figure out what system wikispecies is using so 
    	// i came up with this through http://en.wikipedia.org/wiki/Kingdom_(biology)
    	// an infix of :hansi: means wiki species has no such page, 
    	// but i need this to make the tree into ... a tree. 
    	// ps barbara fischer says its alright, and she knows her shit, 
    	// so i'll leave it at this :) 
    	
    	// two main groups: viruses, all of life 
    	rel.put( "template:hansi:life", "template:hansi:root" );
    	rel.put( "template:virus", "template:hansi:root" ); 
    	
    	// add eukaryotas to life and a bunch of subgroups to eukaryota
    	rel.put( "template:eukaryota", "template:hansi:life" );
    	rel.put( "template:protista", "template:eukaryota" ); 
    	rel.put( "template:animalia", "template:eukaryota" ); 
    	rel.put( "template:plantae", "template:eukaryota" );
    	
    	// add prokaryotas and subgroups too, they're nice! 
    	// it's getting fuzzy here. but i just set bacterie as a subgroup of prokaryota for now, 
    	// not sure thats correct. 
    	rel.put( "template:hansi:prokaryota", "template:hansi:life" ); 
    	rel.put( "template:archaea", "template:hansi:prokaryota" );
    	rel.put( "template:bacteria", "template:hansi:prokaryota" ); 
    	
    	
		/////////////////////////////////////////////////////
		/////////////////////////////////////////////////////
		/////////////////////////////////////////////////////
    	// Step 2: parse the wiki species dump
    	
    	IArticleFilter handler = new IArticleFilter() {
			public void process( WikiArticle article, Siteinfo site ) throws SAXException {
				if( article.getText() == null ){
					System.err.println( "Empty article: " + article.getTitle() );
					return; 
				}
				
				String title = article.getTitle().toLowerCase(); 
				
				// remove all images from the text before doing anything. they sortof annoy us! 
				String text = imageRemover.matcher( article.getText().toLowerCase() ).replaceAll( "" );
				
				// normal article
				if( article.getNamespace().equals( "" ) ){
					// do we have taxo info?
					Matcher matcher = articleTaxo.matcher( text ); 
					if( matcher.find() ){
						addRelToTemplate( title, matcher.group( 1 ) ); 
					}
					else if( ( matcher = articleAltTaxo.matcher( text )  ).find() ){
						addRelToArticle( title, matcher.group( 1 ) ); 
					}
					else if( ( matcher = redirect.matcher( text )  ).find() ){
//						addRelToArticle( title, matcher.group( 1 ) );
						redirects.put( title, matcher.group( 1 ).trim() ); 
					}
					else{
						if( shout ) System.err.println( "skip: " + title ); 
					}
				}
				// template
				else if( article.getNamespace().equals( "Template" ) ){
					Matcher matcher = templateTaxo.matcher( mainLinkRemover.matcher( text ).replaceAll( "" ) ); 
					if( matcher.find() ){
						addRelToTemplate( title, matcher.group( 1 ) ); 
					}
					else if( ( matcher = redirect.matcher( text )  ).find() ){
//						if( shout ) System.out.println( "redir(ignore): " + title + " -> " + matcher.group( 1 ) );
//						addRelToArticle( title, matcher.group( 1 ) );
						redirects.put( title, matcher.group( 1 ).trim() ); 
					}
					else if( ( matcher = templateAltTaxo.matcher( text ) ).find() ){
						addRelToTemplate( title, matcher.group( 1 ) ); 
					}
					else{
						if( shout ) System.err.println( "skip: " + title ); 
					}
				}
				
				count ++; 
				if( count % 1000 == 0 )
					System.out.println( count ); 
			}
		};
		
		WikiXMLParser wxp = new WikiXMLParser(reader, handler);
		wxp.parse();
		
		
		/////////////////////////////////////////////////////
		/////////////////////////////////////////////////////
		/////////////////////////////////////////////////////
		// Step 3: Replace all redirects
		// note that the redirect value can always only occur on 
		// the right sight of the child->parent hashmap, because 
		// redirects are not added to the rel table.
		// also this will cause some funky results with double redirects, 
		// lets just hope there are none. 
		int replaced = 0; 
		for( Entry<String, String> entry : rel.entrySet() ){
			String redirectTo = redirects.get( entry.getValue() );
			if( redirectTo != null ){
				entry.setValue( redirectTo );
				replaced ++; 
			}
		}
		
		System.out.println( "Replaced " + replaced + " redirects" ); 
		System.out.println( "A total of " + redirects.size() + " are stored in the dump file" ); 
		
		
		/////////////////////////////////////////////////////
		/////////////////////////////////////////////////////
		/////////////////////////////////////////////////////
		// Step 4: invert the tree (before we stored as child -> parent, 
		// this creates a parent->children tree)
		// at the same time we get rid of all children that are not 
		// connected somehow to template:hansi:root. 
		Hashtable<String,Set<String>> bottomUp = buildTree( rel ); 
		
		count = 0; 
		LinkedList<String> fringe = new LinkedList<String>(); 
		fringe.add( "template:hansi:root" ); 
		HashSet<String> disconnected = new HashSet<String>( rel.keySet() );
		while( !fringe.isEmpty() ){
			String key = fringe.pop(); 
			disconnected.remove( key ); 
			Set<String> children = bottomUp.get( key );
			if( children != null ){
				for( String child : children ){
					fringe.add( child );
				}
				bottomUp.remove( key ); 
			}
		}
		
		System.out.println( "Prune disconnected... (" + disconnected.size() + ")" ); 
		for( String key : disconnected ){
			System.err.println( "remove " + key + " -> " + rel.get( key ) );
			rel.remove( key ); 
		}

		
		
		/////////////////////////////////////////////////////
		/////////////////////////////////////////////////////
		/////////////////////////////////////////////////////
		// Step 5: Madness breaks out. There are many relations 
		// like thing -> template:thing, but template:thing has no
		// other children so we'd like to simply removing template:thing 
		// and put thing in its place. 
		System.out.println( "Finding redundant template entries" );
		HashSet<String> redundant = new HashSet<>();
		
		for( Entry<String, Set<String>> entry : buildTree( rel ).entrySet() ){
			String dad = entry.getKey(); 
			for( String kid : entry.getValue() ){
				if( dad.equals( "template:" + kid ) ){
//					System.out.println( "Ah, pointless: " + entry.getKey() + " † " + entry.getValue().iterator().next() );
					if( dad.indexOf( ":hansi:" ) > 0 ){
						System.err.println( "Protect: " + kid + "/" + dad ); 
					}
					else{
						redundant.add( dad ); 
					}
				}
			}
		}
		
		System.out.println( "Old rel size: " + rel.size() ); 
		System.out.println( "Getting rid of " + redundant.size() + " redundant template entries" );
		for( Entry<String, String> entry : rel.entrySet() ){
			if( redundant.contains( entry.getValue() ) ){
				// link to the new dad (no template: prefix)
				entry.setValue( entry.getValue().substring( "template:".length() ) ); 
			}
			if( redundant.contains( "template:" + entry.getKey() ) ){
				// link to the previous dads parent
				entry.setValue( rel.get( "template:" + entry.getKey() ) ); 
			}
		}
		for( String obsolete : redundant ){
			rel.remove( obsolete ); 
		}
		System.out.println( "New rel size: " + rel.size() ); 

		
		/////////////////////////////////////////////////////
		/////////////////////////////////////////////////////
		/////////////////////////////////////////////////////

		
		count = 0; 
		System.out.println( "Writing CSV..." ); 
		PrintWriter out = new PrintWriter( "pruned-names.csv" ); 
		
		for( Map.Entry<String, String> entry : rel.entrySet() ){
			count ++;
			out.println( entry.getKey() + "$" + entry.getValue() );
		}
		
		System.out.println( "wrote " + count + " of ~500k" ); 
		
		out.close(); 
    }
    
    
    /**
     * 
     * @param a A normal article title 
     * @param b A template 
     */
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
    
    /**
     * This looks really fucking pointless to me now, no clue why this ever 
     * became its own method. i'll leave it here to praise abstraction hell. 
     * @param a
     * @param b
     */
    static void addRelToArticle( String a, String b ){
    	rel.put( a,  b.trim() ); 
		if( shout ) System.out.println( a + " -> " + b ); 
    }
    
    
    /**
     * flips a tree from a child->parent to a 
     * parent->children structure. 
     * @param rel
     * @return
     */
	public static Hashtable<String, Set<String>> buildTree( Map<String, String> rel ){
		Hashtable<String, Set<String>> result = new Hashtable<String, Set<String>>(); 
		for( Map.Entry<String, String> entry : rel.entrySet() ){
			String child = entry.getKey(); 
			String parent = entry.getValue();
			Set<String> successors = result.get( parent ); 
			if( successors == null ){
				result.put( parent, successors = new HashSet<String>() );  
			}
			successors.add( child ); 
		}
		
		return result;
	}

}

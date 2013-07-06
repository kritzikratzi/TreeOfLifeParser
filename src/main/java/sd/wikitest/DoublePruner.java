package sd.wikitest;

/**
 * um ... this file is sortof old now, its built into prunedCSV.java 
 * 
 * this file takes the pruned-names.csv and attempts to 
 * get rid of some of redundant nodes.  
 */
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class DoublePruner {

	
	public static void main(String[] args) throws IOException {
		System.out.println( "Reading pruned-names.csv ... " ); 
		final Hashtable<String, String> rel = new Hashtable<String, String>();
		BufferedReader in = new BufferedReader( new InputStreamReader( new FileInputStream( "pruned-names.csv" ) ) );
		String line; 
		while( ( line = in.readLine() ) != null ){
			String[] e = line.split( "\\$" );
			rel.put( e[0], e[1] ); 
		}
		System.out.println( "Loaded " + rel.size() + " relations" );
		
		System.out.println( "Creating bottom up map" ); 
		Hashtable<String,Set<String>> bottomUp = buildTree( rel );  
		
		System.out.println( "Finding redundant template entries" );
		HashSet<String> redundant = new HashSet<>();
		
		for( Entry<String, Set<String>> entry : bottomUp.entrySet() ){
			String dad = entry.getKey(); 
			for( String kid : entry.getValue() ){
				if( dad.equals( "template:" + kid ) ){
					System.out.println( "Ah, pointless: " + entry.getKey() + " † " + entry.getValue().iterator().next() );
					redundant.add( dad ); 
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
		int templates = 0; 
		for( String key : rel.keySet() ){
			if( key.startsWith( "template:" ) ){
				templates ++; 
			}
		}
		System.out.println( "Remaining template tags: " + templates ); 
		
		in.close(); 
	}
	

	
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

package sd.wikitest;

import sd.wikitest.Utils.TemplateInfo;

/**
 * See 
 * http://en.wikipedia.org/wiki/Template:Taxobox
 * 
 * @author hansi
 *
 */
public class Taxobox {

	public final static String taxos[] = {
		"regnum", 
		"divisio ", 
		"classis ", 
		"ordo ", 
		"familia ", 
		"genus ", 
		"species"
	}; 
	
	
	public static boolean isSafe( TemplateInfo info ){
		return false;
		// classification_status = disputed

	}
	
	public static String getImage( TemplateInfo info ){
		return null;
		// image         = Sweetbay1082.jpg
	}
	public static void getHighestTaxo( TemplateInfo info ){
		
	}
}

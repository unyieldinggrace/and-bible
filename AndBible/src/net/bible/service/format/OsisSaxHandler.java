package net.bible.service.format;


import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import net.bible.service.format.osistohtml.HtmlTextWriter;
import net.bible.service.sword.Logger;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
/**
 * Convert OSIS input into Canonical text (used when creating search index)
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class OsisSaxHandler extends DefaultHandler {
    
    // debugging
    private boolean isDebugMode = false;

    private HtmlTextWriter writer;
    
    private static final Logger log = new Logger("OsisSaxHandler");
    
    public OsisSaxHandler() {
        writer = new HtmlTextWriter();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    /* @Override */
    public String toString() {
        return writer.getHtml();
    }

    /** return verse from osis id of format book.chap.verse
     * 
     * @param ososID osis Id
     * @return verse number
     */
    protected int osisIdToVerseNum(String osisID) {
       /* You have to use "\\.", the first backslash is interpreted as an escape by the
        Java compiler, so you have to use two to get a String that contains one
        backslash and a dot, which is what you want the regexp engine to see.*/
    	if (osisID!=null) {
	        String[] parts = osisID.split("\\.");
	        if (parts.length>1) {
	            String verse =  parts[parts.length-1];
	            return Integer.valueOf(verse);
	        }
    	}
        return 0;
    }

    protected String getName(String eName, String qName) {
        if (eName!=null && eName.length()>0) {
            return eName;
        } else {
            return qName; // not namespace-aware
        }
    }
    
	protected void write(String s) {
		writer.write(s);
	}

    /** check the value of the specified attribute and return true if same as checkvalue
     * 
     * @param attrs
     * @param attrName
     * @param checkValue
     * @return
     */
    protected boolean isAttrValue(Attributes attrs, String attrName, String checkValue) {
    	if (attrs==null) {
    		return false;
    	}
    	String value = attrs.getValue(attrName);
    	return checkValue.equals(value);
    }
    
    protected void debug(String name, Attributes attrs, boolean isStartTag) {
	    if (isDebugMode) {
	        write("*"+name);
	        if (attrs != null) {
	          for (int i = 0; i < attrs.getLength(); i++) {
	            String aName = attrs.getLocalName(i); // Attr name
	            if ("".equals(aName)) aName = attrs.getQName(i);
	            write(" ");
	            write(aName+"=\""+attrs.getValue(i)+"\"");
	          }
	        }
	        write("*\n");
	    }
    }    

	public void setDebugMode(boolean isDebugMode) {
		this.isDebugMode = isDebugMode;
	}
	protected void reset() {
		writer.reset();
	}

	public HtmlTextWriter getWriter() {
		return writer;
	}
}

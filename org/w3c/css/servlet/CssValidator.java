//
// $Id$
// From Philippe Le Hegaret (Philippe.Le_Hegaret@sophia.inria.fr)
//
// (c) COPYRIGHT MIT and INRIA, 1997.
// Please first read the full copyright statement in file COPYRIGHT.html

package org.w3c.css.servlet;

import org.w3c.css.css.CssParser;
import org.w3c.css.css.DocumentParser;
import org.w3c.css.css.StyleReport;
import org.w3c.css.css.StyleReportFactory;
import org.w3c.css.css.StyleSheet;
import org.w3c.css.css.StyleSheetParser;
import org.w3c.css.css.TagSoupStyleSheetHandler;
import org.w3c.css.error.ErrorReport;
import org.w3c.css.error.ErrorReportFactory;
import org.w3c.css.index.IndexGenerator;
import org.w3c.css.properties.PropertiesLoader;
import org.w3c.css.util.ApplContext;
import org.w3c.css.util.Codecs;
import org.w3c.css.util.FakeFile;
import org.w3c.css.util.HTTPURL;
import org.w3c.css.util.NVPair;
import org.w3c.css.util.Utf8Properties;
import org.w3c.css.util.Util;
import org.w3c.www.mime.MimeType;
import org.w3c.www.mime.MimeTypeFormatException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ProtocolException;
import java.net.URL;

/**
 * This class is a servlet to use the validator.
 * 
 * @version $Revision$
 */
public final class CssValidator extends HttpServlet {

    final static String texthtml    = "text/html";

    final static String applxhtml   = "application/xhtml+xml";
    
    final static String textplain   = "text/plain";
    
    final static String textcss   = "text/css";

    final static String textunknwon = "text/unknown";

    final static String soap12      = "application/soap+xml";

    final static String json      = "application/json";

    final static String server_name = "Jigsaw/2.2.5 "
	+ "W3C_CSS_Validator_JFouffa/2.0";

    final static String headers_name = "X-W3C-Validator-";
    /**
     * Create a new CssValidator.
     */
    public CssValidator() {
    }

    /**
     * Initializes the servlet and logs the initialization. The init method is
     * called once, automatically, by the network service each time it loads
     * the servlet. It is guaranteed to finish before any service requests are
     * accepted. On fatal initialization errors, an UnavailableException should
     * be thrown. Do not call the method System.exit.
     *
     * <p>
     * The init method stores the ServletConfig object. Servlet writers who
     * specialize this method should call either super.init, or store the
     * ServletConfig object themselves. If an implementor decides to store the
     * ServletConfig object in a different location, then the getServletConfig
     * method must also be overridden.
     *
     * <P>
     * <DL>
     * <STRONG>Init parameters:</STRONG>
     * <DT>debug
     * <DD><code>true</code> if you want to be in debug mode.
     * <DT>aural
     * <DD><code>true</code> if you want to be in aural mode.
     * <DT>import
     * <DD><code>false</code> if you don't want to activate the import
     * statement. For security reasons, you shoud be careful when you lunch the
     * servlet on a HTTP server with special access authorization.
     * <DT>input
     * <DD><code>html</code> if the user have an HTML input or
     * <code>xml</code> otherwise. <strong>deprecated</strong>
     * </DL>
     *
     * @param config
     *            servlet configuration information.
     * @exception ServletException
     *                if a servlet exception has occurred.
     */
    public void init(ServletConfig config) throws ServletException {
	super.init(config);

	// [SECURITY] don't forget this !
	Util.servlet = true;

	if (config.getInitParameter("debug") != null) {
	    // servlet debug mode
	    // define a boolean property CSS.StyleSheet.debug if you want more
	    // debug.
	    Util.onDebug = config.getInitParameter("debug").equals("true");
	    System.err.println("RUN IN DEBUG MODE: "
			       + config.getInitParameter("debug").equals("true"));
	} else if (Util.onDebug) {
	    System.err.println("RUN IN DEBUG MODE"+
	    		       " but activated outside the servlet");
	}

	if ((config.getInitParameter("import") != null)
	    && (config.getInitParameter("import").equals("false"))) {
	    Util.importSecurity = true;
	}
	
	// The following code will check if the index files are missing or outdated
	// If so, the files will be regenerated
	// This is done in a Thread so that the validation can carry on.
	new Thread () {
	    public void run () {
		IndexGenerator.generatesIndex(true);
	    }
	}.start();

    }

    private PrintWriter getLocalPrintWriter(OutputStream os, String encoding)
	throws IOException {
	if (encoding != null) {
	    return new PrintWriter(new OutputStreamWriter(os, encoding));
	} else {
	    return new PrintWriter(new OutputStreamWriter(os,
							  Utf8Properties.ENCODING));
	}
    }

    /**
     * Performs the HTTP GET operation.
     * An HTTP BAD_REQUEST error is reported if
     * an error occurs. This servlet writers shouldn't set the headers for the
     * requested entity (content type and encoding).
     *
     * <P>
     * Note that the GET operation is expected to be <em>safe</em>, without
     * any side effects for which users might be held responsible. For example,
     * most form queries have no side effects. Requests intended to change
     * stored data should use some other HTTP method. (There have been cases of
     * significant security breaches reported because web-based applications
     * used GET inappropriately.)
     *
     * <P>
     * The GET operation is also expected to be <em>idempotent</em>, meaning
     * that it can safely be repeated. This is not quite the same as being
     * safe, but in some common examples the requirements have the same result.
     * For example, repeating queries is both safe and idempotent
     * (unless payment is required!), but buying something or modifying data
     * is neither safe nor idempotent.
     *
     * <P>
     * <DL>
     * <STRONG>Forms parameters:</STRONG>
     * <DT>URL
     * <DD>the URL to be parsed.
     * <DT>submitURL
     * <DD>if the user want to parse an URL.
     * <DT>text
     * <DD>The text to be parsed.
     * <DT>submitTEXT
     * <DD>if the user want to parse the text.
     * <DT>output
     * <DD>HTML if the user want an HTML output or XML otherwise.
     * <DT>input
     * <DD>HTML if the user have an HTML input or XML otherwise.
     * </DL>
     *
     * @param req
     *            encapsulates the request to the servlet.
     * @param res
     *            encapsulates the response from the servlet.
     * @exception ServletException
     *                if the request could not be handled.
     * @exception IOException
     *                if detected when handling the request.
     * @see org.w3c.css.css.StyleSheetGenerator
     */
    public void doGet(HttpServletRequest req, HttpServletResponse res)
	throws ServletException, IOException {


	boolean errorReport = true;
	int warningLevel = 2;
	CssParser parser = null;

	String lang = null;	
	try {
	    lang = req.getParameter("lang");
	}
	catch(Exception e) {
	    lang = null;
	}
	
	if(lang == null || lang.equals("")) {
	    lang = req.getHeader("Accept-Language");
	}
	else {
	    lang += ',' + req.getHeader("Accept-Language");
	}
	ApplContext ac = new ApplContext(lang);
	ac.setLink(req.getQueryString());
	ac.setContentEncoding(req.getHeader("Accept-Charset"));
	String output = req.getParameter("output");
	
	String uri = null;
	try {
	    uri = req.getParameter("uri"); // null if the parameter does not
	    // exist
	} catch (Exception ex) {
	    // pb in URI decoding (bad escaping, most probably)
	    handleError(res, ac, output, "No file", new IOException(
								    "Invalid escape sequence in URI"), false);
	}
	String text = null;
	try {
	    text = req.getParameter("text");            
	} catch (Exception ex) {
	    // pb in URI decoding (bad escaping, most probably)
	    // not sure it will work here, as it may be catched by the first
	    // getParameter call
	    handleError(res, ac, output, "Invalid text", new IOException(
									 "Invalid escape sequence in URI"), false);
	}

	String warning = req.getParameter("warning");
	String error = req.getParameter("error");
	String profile = req.getParameter("profile");
	String usermedium = req.getParameter("usermedium");
	String type = req.getParameter("type");
	if (type == null)
	    type = "none";

	String credential = req.getHeader("Authorization");
	if ((credential != null) && (credential.length() > 1)) {
	    ac.setCredential(credential);
	}

	if (usermedium == null || "".equals(usermedium)) {
	    usermedium = "all";
	}

	InputStream in = req.getInputStream();

	ac.setMedium(usermedium);

	if (req.getParameter("debug") != null) {
	    Util.onDebug = req.getParameter("debug").equals("true");
	    if (Util.onDebug) {
		System.err.println("SWITCH DEBUG MODE REQUEST");
	    }
	}
	else {
	    Util.onDebug = false;
	}

	//text = Util.suppressWhiteSpace(text);
	uri = Util.suppressWhiteSpace(uri);

	if (output == null) {
	    output = texthtml;
	}

	// CSS version
	if (profile != null && (!"none".equals(profile) || "".equals(profile))) {
	    if ("css1".equals(profile) || "css2".equals(profile)
		|| "css21".equals(profile)
		|| "css3".equals(profile) || "svg".equals(profile)
		|| "svgbasic".equals(profile) || "svgtiny".equals(profile)) {
	    	ac.setCssVersion(profile);
	    } else {
		ac.setProfile(profile);
		ac.setCssVersion(PropertiesLoader.config.getProperty("defaultProfile"));
	    }
	} else {
	    ac.setProfile("none");
	    ac.setCssVersion(PropertiesLoader.config.getProperty("defaultProfile"));
	}
	if (Util.onDebug) {
	    System.err.println("[DEBUG]  profile is : " + ac.getCssVersion()
			       + " medium is " + usermedium);
	}

	// verify the request
	if ((uri == null) && (text == null)) {
	    // res.sendError(res.SC_BAD_REQUEST,
	    // "You have send an invalid request.");
	    handleError(res, ac, output, "No file",
			new IOException(ac.getMsg().getServletString("invalid-request")),
			false);
	    return;
	}

	in.close();

	// set the warning output
	if (warning != null) {
	    if (warning.equals("no")) {
		warningLevel = -1;
	    } else {
		try {
		    warningLevel = Integer.parseInt(warning);
		} catch (Exception e) {
		    System.err.println(e);
		}
	    }
	    ac.setWarningLevel(warningLevel);
	}

	// set the error report
	if (error != null && error.equals("no")) {
	    errorReport = false;
	}

	// debug mode
	Util.verbose("\nServlet request ");
	if (uri != null) {
	    Util.verbose("Source file : " + uri);
	} else {
	    Util.verbose("TEXTAREA Input");
	}
	// verbose("From " + req.getRemoteHost() +
	// " (" + req.getRemoteAddr() + ") at " + (new Date()) );
	
	if (uri != null) {
	    // HTML document
	    try {
		uri = HTTPURL.getURL(uri).toString(); // needed to be sure
		// that it is a valid
		// url
		uri = uri.replaceAll(" ", "%20");
		DocumentParser URLparser = new DocumentParser(ac, uri);

		handleRequest(ac, res, uri, URLparser.getStyleSheet(), output,
			      warningLevel, errorReport);
	    } catch (ProtocolException pex) {
		if (Util.onDebug) {
		    pex.printStackTrace();
		}
		res.setHeader("WWW-Authenticate", pex.getMessage());
		res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
	    } catch (Exception e) {
		handleError(res, ac, output, uri, e, true);
	    }
	} else if (text != null) {
	    String fileName = "TextArea";
	    Util.verbose("- " + fileName + " Data -");
	    Util.verbose(text);
	    Util.verbose("- End of " + fileName + " Data");
	    InputStream is = new ByteArrayInputStream(text.getBytes());
	    fileName = "file://localhost/" + fileName;
		
	    try {
		
		if ("css".equals(type) || ( "none".equals(type) && isCSS(text))) {
		    // if CSS:
		    parser = new StyleSheetParser();
		    parser.parseStyleElement(ac, is, null, usermedium,
					     new URL(fileName), 0);
		  	  
		    handleRequest(ac, res, fileName, parser
				  .getStyleSheet(), output, warningLevel, errorReport);
		} else {
		    // else, trying HTML
		    TagSoupStyleSheetHandler handler = new TagSoupStyleSheetHandler(null, ac);
		    handler.parse(is, fileName);
	
		    handleRequest(ac, res, fileName, handler.getStyleSheet(), output,
				  warningLevel, errorReport);
		}
	    } catch (ProtocolException pex) {
		if (Util.onDebug) {
		    pex.printStackTrace();
		}
		res.setHeader("WWW-Authenticate", pex.getMessage());
		res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
	    } catch (Exception e) {
		handleError(res, ac, output, fileName, e, false);
	    }
	}
	Util.verbose("CssValidator: Request terminated.\n");
    }

    /**
     * This method is used for the direct input
     * If the &lt;style&gt; tag is found, it may be an HTML entry
     * The exception is when this tag is inside comment
     * It might also be an HTML document with no CSS => why ?
     * Or with only imports (we can't chack thoses imports...)
     * @param text, the textarea to test
     * @return <tt>false</tt> if it contains the style tag well formed 
     */
    private boolean isCSS(String text) {
	try {
	    text = text.toLowerCase();
	    int p = text.indexOf("<style");
	    return p == -1 || p > text.indexOf("</style>");
	} catch (Exception e) {
	    System.err.println("error: " + e.getMessage());
	    return true;
	}
		
    }

    /**
     * Performs the HTTP POST operation. An HTTP BAD_REQUEST error is reported
     * if an error occurs. The headers that are set should include content type,
     * length, and encoding. Setting content length allows the servlet to take
     * advantage of HTTP "connection keep alive". If content length can not be
     * set in advance, the performance penalties associated with not using keep
     * alives will sometimes be avoided if the response entity fits in an
     * internal buffer. The servlet implementor must write the headers before
     * the response data because the headers can be flushed at any time after
     * the data starts to be written.
     *
     * <P>
     * This method does not need to be either "safe" or "idempotent". Operations
     * requested through POST could be ones for which users need to be held
     * accountable. Specific examples including updating stored data or buying
     * things online.
     *
     * <P>
     * <DL>
     * <STRONG>Forms parameters:</STRONG>
     * <DT>file
     * <DD>The input file to be parsed.
     * <DT>output
     * <DD>The format output.
     * <DT>input
     * <DD>HTML if the user have an HTML input or XML otherwise.
     * </DL>
     *
     * @param req
     *            encapsulates the request to the servlet
     * @param res
     *            encapsulates the response from the servlet
     * @exception ServletException
     *                if the request could not be handled
     * @exception IOException
     *                if detected when handling the request
     * @see org.w3c.css.css.StyleSheetGenerator
     */
    public void doPost(HttpServletRequest req, HttpServletResponse res)
	throws ServletException, IOException {

	String lang = null;	
	try {
	    lang = req.getParameter("lang");
	}
	catch(Exception e) {
	    lang = null;
	}

	boolean errorReport = true;
	int warningLevel = 2;
	CssParser parser = null;
	FakeFile file = null;
	String text = null;
	String output = null;
	//boolean XMLinput = false;
	String warning = null;
	String error = null;
	String profile = "none";
	String usermedium = "all";

	ServletInputStream in = req.getInputStream();

	byte[] buf = new byte[2048];
	byte[] general = new byte[65536];
	int count = 0;
	int len;

	if (req.getParameter("debug") != null) {
	    Util.onDebug = req.getParameter("debug").equals("true");
	    if (Util.onDebug) {
		System.err.println("SWITCH DEBUG MODE REQUEST");
	    }
	}
	else {
	    Util.onDebug = false;
	}

	Util.verbose("\nCssValidator: Servlet request ");
	// verbose("From " + req.getRemoteHost() +
	// " (" + req.getRemoteAddr() + ") at " + (new Date()) );
	Util.verbose("Content-length : " + req.getContentLength());

	if (req.getContentType().trim().startsWith("multipart/form-data")) {
	    Util.verbose("Content-type : multipart/form-data");
	}

	try {
	    while ((len = in.readLine(buf, 0, buf.length)) != -1) {
		if (len >= 2 && buf[len - 1] == '\n' && buf[len - 2] == '\r') {
		    len -= 1;
		    buf[len - 1] = (byte) '\n';
		}
		if (len != 0 && buf[len - 1] == '\r') {
		    buf[len - 1] = (byte) '\n';
		}

		if (general.length < (count + len)) {
		    byte[] old = general;
		    general = new byte[old.length * 2];
		    System.arraycopy(old, 0, general, 0, old.length);
		}
		System.arraycopy(buf, 0, general, count, len);
		count += len;
	    }
	} finally {
	    in.close();
	}
	
	try {
	    buf = new byte[count];
	    System.arraycopy(general, 0, buf, 0, count);
	    NVPair[] tmp = Codecs.mpFormDataDecode(buf, req.getContentType());
	    for (int i = 0; i < tmp.length; i++) {
		if (tmp[i].getName().equals("file")) {
		    file = (FakeFile) tmp[i].getValue();
		} else if (tmp[i].getName().equals("text")) {
		    text = (String) tmp[i].getValue();
		} else if (tmp[i].getName().equals("lang")) {
		    lang = (String) tmp[i].getValue();
		} else if (tmp[i].getName().equals("output")) {
		    output = (String) tmp[i].getValue();
		} else if (tmp[i].getName().equals("warning")) {
		    warning = (String) tmp[i].getValue();
		} else if (tmp[i].getName().equals("error")) {
		    warning = (String) tmp[i].getValue();
		    //} else if (tmp[i].getName().equals("input")) {
		    //    XMLinput = ((String) tmp[i].getValue()).equals("XML");
		} else if (tmp[i].getName().equals("profile")) {
		    profile = (String) tmp[i].getValue();
		} else if (tmp[i].getName().equals("usermedium")) {
		    usermedium = (String) tmp[i].getValue();
		    if (usermedium == null || "".equals(usermedium)) {
			usermedium = "all";
		    }
		}
	    }
	} catch (Exception e) {
	    System.out.println("Oups! Error in Util/Codecs.java?!?");
	    e.printStackTrace();
	}

	
	if(lang == null || lang.equals("")) {
	    lang = req.getHeader("Accept-Language");
	}
	else {
	    lang += ',' + req.getHeader("Accept-Language");
	}
	
	ApplContext ac = new ApplContext(lang);
	ac.setLink(req.getQueryString());
	ac.setMedium(usermedium);

	if (output == null) {
	    output = texthtml;
	}
	if ((file == null || file.getSize() == 0) && 
	    (text == null || text.length() == 0)) {
	    // res.sendError(res.SC_BAD_REQUEST,
	    // "You have send an invalid request");
	    handleError(res, ac, output, "No file",
		    	new IOException(ac.getMsg().getServletString(
							   "invalid-request")),
		    	false);
	    return;
	}

	// set the warning output
	if (warning != null) {
	    if (warning.equals("no")) {
		warningLevel = -1;
	    } else {
		try {
		    warningLevel = Integer.parseInt(warning);
		} catch (Exception e) {
		    System.err.println(e);
		}
	    }
	    ac.setWarningLevel(warningLevel);
	}

	// set the error report
	if (error != null && error.equals("no")) {
	    errorReport = false;
	}

	// CSS version
	if (profile != null && (!"none".equals(profile) ||"".equals(profile))) {
	    if ("css1".equals(profile) || "css2".equals(profile)
		|| "css21".equals(profile)
		|| "css3".equals(profile) || "svg".equals(profile)
		|| "svgbasic".equals(profile) || "svgtiny".equals(profile)) {
	    	ac.setCssVersion(profile);
	    } else {
		ac.setProfile(profile);
		ac.setCssVersion(PropertiesLoader.config.getProperty(
							     "defaultProfile"));
	    }
	} else {
	    ac.setProfile("none");
	    ac.setCssVersion(PropertiesLoader.config.getProperty(
							     "defaultProfile"));
	}
	String fileName = "";
	InputStream is = null;
	boolean isCSS = false;
	
	if (file != null && file.getSize() != 0) {
	    ac.setFakeFile(file);
	    fileName = file.getName();
	    Util.verbose("File : " + fileName);
	    // another way to get file type...
	    isCSS = file.getContentType().equals(textcss);
	} else if (text != null ) {
	    ac.setFakeText(text);
	    fileName = "TextArea";
	    Util.verbose("- " + fileName + " Data -");
	    Util.verbose(text);
	    Util.verbose("- End of " + fileName + " Data");
	    //quick test that works in most cases to determine wether it's 
	    //HTML or CSS
	    isCSS = isCSS(text);
	}
	fileName = "file://localhost/" + fileName;
  	try {
	    URL u = new URL(fileName);
	    is = ac.getFakeInputStream(u);

	    ac.setFakeURL(fileName);
	    if (isCSS) {
		//if CSS:
		parser = new StyleSheetParser();
		parser.parseStyleElement(ac, is, null, usermedium,
					 new URL(fileName), 0);
	  	  
		handleRequest(ac, res, fileName, parser.getStyleSheet(),
			      output, warningLevel, errorReport);
	    } else {
		// else, trying HTML
		TagSoupStyleSheetHandler handler;
		handler = new TagSoupStyleSheetHandler(null, ac);
		handler.parse(is, fileName);

		handleRequest(ac, res, fileName, handler.getStyleSheet(),
			      output, warningLevel, errorReport);
	    }
	} catch (ProtocolException pex) {
	    if (Util.onDebug) {
		//pex.printStackTrace();
	    }
	    res.setHeader("WWW-Authenticate", pex.getMessage());
	    res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
  	} catch (Exception e) {
  	    handleError(res, ac, output, fileName, e, false);
  	}
  
  
	Util.verbose("CssValidator: Request terminated.\n");
    }

    private void handleRequest(ApplContext ac, HttpServletResponse res,
			       String title, StyleSheet styleSheet,
			       String output, int warningLevel,
			       boolean errorReport) throws Exception {

	buildHeader(ac, res, output);

	if (styleSheet == null) {
	    throw new IOException(ac.getMsg().getServletString("process") + " "
				  + title);
	}

	// if the output parameter was a mime type, we convert it
	// to an understandable value for the StyleReportFactory
	if ("text/xml".equals(ac.getInput()) && texthtml.equals(output)) {
	    output = "xhtml";
	} else if (texthtml.equals(output)) {
	    output = "html";
	} else if (soap12.equals(output)) {
	    output = "soap12";
	} else if (json.equals(output)) {
	    output = "json";
	} else if(textplain.equals(output)) {
	    output = "text";
	}
	styleSheet.findConflicts(ac);

	StyleReport style = StyleReportFactory.getStyleReport(ac, title,
							      styleSheet, output, warningLevel);
	if (!errorReport) {
	    style.desactivateError();
	}
	PrintWriter out = getLocalPrintWriter(res.getOutputStream(), ac
					      .getContentEncoding());
	int nb_errors = styleSheet.getErrors().getErrorCount();
	res.setHeader(headers_name + "Errors", String.valueOf(nb_errors));
	res.setHeader(headers_name + "Status", nb_errors == 0 ? "Valid" : "Invalid");

	try {
	    style.print(out);
	    
	} finally {
	    out.close();
	}
    }

    /**
     * Generates the response header
     * @param ac
     * @param res
     * @param output
     * @throws MimeTypeFormatException
     */
    private void buildHeader(ApplContext ac, HttpServletResponse res, 
			     String output)
    {
	// I don't want cache for the response (inhibits proxy)
	res.setHeader("Pragma", "no-cache"); // @@deprecated
	res.setHeader("Cache-Control", "no-cache");
	// Here is a little joke :-)
	// res.setHeader("Server", server_name);

	if(output == null) {
	    output = new String(texthtml);
	}

	// set the content-type for the response
	MimeType outputMt = null;
	if (output.equals(texthtml) || output.equals("html")) {	    
	    outputMt = MimeType.TEXT_HTML.getClone();
	} else if (output.equals(applxhtml) || output.equals("xhtml")) {
	    outputMt = MimeType.APPLICATION_XHTML_XML.getClone();
	} else if (output.equals(soap12) || output.equals("soap12")) {
	    // invert the comments on the following lines to (de)activate
	    // the soap Mime Type
	    try {
		outputMt = new MimeType(soap12);
	    }
	    catch (MimeTypeFormatException e) {
		outputMt = MimeType.TEXT_PLAIN.getClone();
	    }
	    //outputMt = MimeType.TEXT_PLAIN.getClone();
	} else if(output.equals("ucn")) {
	    outputMt = MimeType.APPLICATION_XML.getClone();
        } else if(output.equals("json")) {
            try {
                outputMt = new MimeType(json);
            }
            catch (MimeTypeFormatException e) {
                outputMt = MimeType.TEXT_PLAIN.getClone();
            }
	} else {
	    // Change this line if you want text/html output when incorrect
	    // output is passed
	    outputMt = MimeType.TEXT_PLAIN.getClone();
	}

	if(ac != null) {
	    // ignore content encoding if output is SOAP
	    if(output.equals("soap12")) {
		ac.setContentEncoding(null);
	    }

	    if (ac.getContentEncoding() != null) {
		outputMt.setParameter("charset", ac.getContentEncoding());
	    }
	    res.setContentType(outputMt.toString());

	    if (ac.getContentLanguage() != null) {
		res.setHeader("Content-Language", ac.getContentLanguage());
	    } else {
		res.setHeader("Content-Language", "en");
	    }
	}
	else {
	    res.setHeader("Content-Language", "en");
	    res.setHeader("charset", Utf8Properties.ENCODING);
	}
	res.setHeader("Vary", "Accept-Language");
    }

    private void handleError(HttpServletResponse res, ApplContext ac,
			     String output, String title, Exception e,
			     boolean validURI)
    	throws IOException {

	System.err.println("[ERROR VALIDATOR] " + title);
	System.err.println(e.toString());
	e.printStackTrace();

	buildHeader(ac, res, output);
	res.setStatus(500);

	if((e instanceof java.net.UnknownHostException) ||
	   ((e instanceof java.io.FileNotFoundException) &&
	    ((e.getMessage().indexOf("Not Found") != -1) ||
	     (e.getMessage().indexOf("Service Unavailable") != -1)))) {
	    validURI = true;
	}
	else {
	    validURI = false;
	}

	PrintWriter out = getLocalPrintWriter(res.getOutputStream(), ac
					      .getContentEncoding());

	ErrorReport error = ErrorReportFactory.getErrorReport(ac, title, output,
							      e, validURI);
	res.setHeader(headers_name + "Status", "Abort");

	try {
	    error.print(out);
	}
	finally {
	    out.close();
	}
    }

}

/*
 *  Copyright 2015-2018 WebPKI.org (http://webpki.org).
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.webpki.saturn.keyprovider;

import java.io.IOException;
import java.util.logging.Logger;
import java.net.URL;
import java.net.URLEncoder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.webpki.keygen2.ServerState;
import org.webpki.webutil.ServletUtil;

public class KeyProviderInitServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    static Logger logger = Logger.getLogger(KeyProviderInitServlet.class.getCanonicalName());

    static final String KEYGEN2_SESSION_ATTR           = "keygen2";

    static final String INIT_TAG = "init";     // Note: This is currently also a part of the KeyGen2 client!
    static final String ABORT_TAG = "abort";
    static final String PARAM_TAG = "msg";
    static final String ERROR_TAG = "err";

    static final String HTML_INIT = 
            "<!DOCTYPE HTML>"+
            "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>" +
            "<link rel=\"icon\" href=\"saturn.png\" sizes=\"192x192\">"+
            "<title>Payment Credential Enrollment</title>"+
            "<style type=\"text/css\">html {overflow:auto} html, body {margin:0px;padding:0px;height:100%} "+
            "body {font-size:8pt;color:#000000;font-family:verdana,arial;background-color:white} "+
            "h2 {font-weight:bold;font-size:12pt;color:#000000;font-family:arial,verdana,helvetica} "+
            "h3 {font-weight:bold;font-size:11pt;color:#000000;font-family:arial,verdana,helvetica} "+
            "a:link {font-weight:bold;font-size:8pt;color:blue;font-family:arial,verdana;text-decoration:none} "+
            "a:visited {font-weight:bold;font-size:8pt;color:blue;font-family:arial,verdana;text-decoration:none} "+
            "a:active {font-weight:bold;font-size:8pt;color:blue;font-family:arial,verdana} "+
            "input {font-weight:normal;font-size:8pt;font-family:verdana,arial} "+
            "td {font-size:8pt;font-family:verdana,arial} "+
            ".smalltext {font-size:6pt;font-family:verdana,arial} "+
            "button {font-weight:normal;font-size:8pt;font-family:verdana,arial;padding-top:2px;padding-bottom:2px} "+
            ".headline {font-weight:bolder;font-size:10pt;font-family:arial,verdana} "+
            "</style>";

    static String getHTML(String javascript, String bodyscript, String box) {
        StringBuilder s = new StringBuilder(HTML_INIT);
        if (javascript != null) {
            s.append("<script type=\"text/javascript\">").append(javascript)
                    .append("</script>");
        }
        s.append("</head><body");
        if (bodyscript != null) {
            s.append(' ').append(bodyscript);
        }
        s.append(
                "><div style=\"cursor:pointer;position:absolute;top:15pt;left:15pt;z-index:5;width:100pt\"" +
                " onclick=\"document.location.href='http://cyberphone.github.io/doc/saturn'\" title=\"Home of Saturn\">")
         .append (KeyProviderService.saturnLogotype)
         .append ("</div><table cellapdding=\"0\" cellspacing=\"0\" width=\"100%\" height=\"100%\">")
                .append(box).append("</table></body></html>");
        return s.toString();
    }
  
    static void output(HttpServletResponse response, String html) throws IOException, ServletException {
        response.setContentType("text/html; charset=utf-8");
        response.setHeader("Pragma", "No-Cache");
        response.setDateHeader("EXPIRES", 0);
        response.getOutputStream().write(html.getBytes("UTF-8"));
    }
    
    static String keygen2EnrollmentUrl;
    
    static String successMessage;
    
    synchronized void initGlobals(String baseUrl) throws IOException {

        ////////////////////////////////////////////////////////////////////////////////////////////
        // Get KeyGen2 protocol entry
        ////////////////////////////////////////////////////////////////////////////////////////////
        keygen2EnrollmentUrl = baseUrl + "/getkeys";

        ////////////////////////////////////////////////////////////////////////////////////////////
        // Show a sign that the user succeeded getting Saturn credentials
        ////////////////////////////////////////////////////////////////////////////////////////////
        URL hostUrl = new URL(keygen2EnrollmentUrl);
        String merchantHost = hostUrl.getHost();
        if (merchantHost.equals("mobilepki.org")) {
            merchantHost = "test.webpki.org";
        }
        String merchantUrl = new URL(hostUrl.getProtocol(), merchantHost, hostUrl.getPort(), "/webpay-merchant").toExternalForm(); 
        logger.info(merchantUrl);
        successMessage = new StringBuilder("<b>Enrollment Succeeded!</b><p><a href=\"")
            .append(merchantUrl)
            .append("\">Continue to merchant site</a></p>").toString();
    }
    
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        if (!request.getHeader("User-Agent").contains("Android")) {
            output(response, 
                    getHTML(null,
                            null,
                            "<tr><td width=\"100%\" align=\"center\" valign=\"middle\">" +
                            "This proof-of-concept system only supports Android</td></tr>"));
            return;
        }
        if (keygen2EnrollmentUrl == null) {
            initGlobals(ServletUtil.getContextURL(request));
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        session = request.getSession(true);
        session.setAttribute(KEYGEN2_SESSION_ATTR,
                             new ServerState(new KeyGen2SoftHSM(KeyProviderService.keyManagementKey), 
                                             keygen2EnrollmentUrl,
                                             KeyProviderService.serverCertificate,
                                             null));

        ////////////////////////////////////////////////////////////////////////////////////////////
        // The following is the actual contract between an issuing server and a KeyGen2 client.
        // The "cookie" element is optional while the "url" argument is mandatory.
        // The "init" argument bootstraps the protocol via an HTTP GET
        ////////////////////////////////////////////////////////////////////////////////////////////
        String urlEncoded = URLEncoder.encode(keygen2EnrollmentUrl, "utf-8");
        String extra = "?cookie=JSESSIONID%3D" + session.getId() +
                       "&url=" + urlEncoded +
                       "&ver=" + KeyProviderService.grantedVersions +
                       "&init=" + urlEncoded + "%3F" + INIT_TAG + "%3Dtrue" +
                       "&cncl=" + urlEncoded + "%3F" + ABORT_TAG + "%3Dtrue";
        output(response, 
               getHTML(null,
                       null,
                       "<tr><td align=\"center\"><table>" +
                       "<tr><td>This proof-of-concept system provisions secure payment<br>" +
                       "credentials to be used in the Android version of the \"Wallet\"<br>&nbsp;</td></tr>" +
                       "<tr><td align=\"center\">" +
                       "<a href=\"intent://keygen2" + extra +
                       "#Intent;scheme=webpkiproxy;" +
                       "package=org.webpki.mobile.android;end\">Start KeyGen2</a></td></tr></table></td></tr>"));
    }
}

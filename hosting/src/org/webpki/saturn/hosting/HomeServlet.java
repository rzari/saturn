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
package org.webpki.saturn.hosting;

import java.io.IOException;

import javax.servlet.ServletException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.webpki.saturn.common.PayeeCoreProperties;
import org.webpki.saturn.common.AuthorityBaseServlet;
import org.webpki.saturn.common.HttpSupport;
import org.webpki.saturn.common.TimeUtil;

// This servlet publishes a miniscule "home page".

public class HomeServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String authorityUrl = HostingService.providerAuthorityUrl;
        StringBuilder html = new StringBuilder(
           AuthorityBaseServlet.TOP_ELEMENT +
           "<link rel=\"icon\" href=\"saturn.png\" sizes=\"192x192\"><title>Saturn Hosting Service</title>" +
           AuthorityBaseServlet.REST_ELEMENT +
           "<body><table>" +
           "<tr><td class=\"header\">Saturn Hosting Service</td></tr>" +
           "<tr><td>This is a " +
           AuthorityBaseServlet.SATURN_LINK +
           " &quot;hosting&quot; server.</td></tr>" +
           "<tr><td>Started: ")
         .append(TimeUtil.displayUtcTime(HostingService.started))
         .append("</td></tr>" +
           "<tr><td>Authority object: " +
           "<a href=\"")
         .append(authorityUrl)
         .append("\" target=\"_blank\">")
         .append(authorityUrl)
         .append("</a>")
         .append("</td></tr><tr><td style=\"padding-bottom:4pt\">Registered merchants:");
        if (HostingService.merchantAccountDb.isEmpty()) {
            html.append(" <i>None</i></td></tr>");
        } else {
            html.append("</td></tr>" +
                "<tr><td><table class=\"tftable\"><tr><th>ID</th><th>Common Name</th><th>Authority Object</th></tr>");
            for (PayeeCoreProperties payeeCoreProperties : HostingService.merchantAccountDb.values()) {
                String id = payeeCoreProperties.getDecoratedPayee().getId();
                authorityUrl = HostingService.payeeAuthorityBaseUrl + id;
                html.append("<tr><td style=\"text-align:right\">")
                 .append(id)
                 .append("</td><td>")
                 .append(payeeCoreProperties.getDecoratedPayee().getCommonName())
                 .append("</td><td><a href=\"")
                 .append(authorityUrl)
                 .append("\" target=\"_blank\">")
                 .append(authorityUrl)
                 .append("</a></td></tr>");
            }
            html.append("</table></td></tr>");
        }
        html.append("</table></body></html>");
        HttpSupport.writeHtml(response, html);
    }
}

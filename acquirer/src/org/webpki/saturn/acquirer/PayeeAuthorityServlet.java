/*
 *  Copyright 2006-2016 WebPKI.org (http://webpki.org).
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.webpki.saturn.acquirer;

import java.io.IOException;

import javax.servlet.ServletException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.webpki.saturn.common.BaseProperties;

//This servlet publishes Payee (Merchant) "PayeeAuthority" objects.

public class PayeeAuthorityServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String id = request.getPathInfo();
        if (id != null) {
            byte[] authorityBlob = AcquirerService.payeeAuthorityList.getAuthorityBlob(id.substring(1));
            if (authorityBlob != null) {
                response.setContentType(BaseProperties.JSON_CONTENT_TYPE);
                response.setHeader("Pragma", "No-Cache");
                response.setDateHeader("EXPIRES", 0);
                response.getOutputStream().write(authorityBlob);
                return;
            }
        }
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
}

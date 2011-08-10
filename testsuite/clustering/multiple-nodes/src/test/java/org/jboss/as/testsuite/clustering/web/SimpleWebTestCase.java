/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.testsuite.clustering.web;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import javax.servlet.http.HttpServletResponse;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.Archive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Validate the <distributable/> works for multiple nodes
 *
 * @author <a href="kpiwko@redhat.com">Karel Piwko</a>
 * @author Paul Ferraro
 */
@RunWith(Arquillian.class)
@RunAsClient
public class SimpleWebTestCase {

    private static String sessionId;

    @Deployment(name = "dep.active-1", testable = false)
    @TargetsContainer("container.active-1")
    public static Archive<?> deploymentOn1() {
        return Deployments.distributabeWar();
    }

    @Deployment(name = "dep.active-2", testable = false)
    @TargetsContainer("container.active-2")
    public static Archive<?> deploymentOn2() {
        return Deployments.distributabeWar();
    }

    @Deployment(name = "dep.active-3", testable = false)
    @TargetsContainer("container.active-3")
    public static Archive<?> deploymentOn3() {
        return Deployments.distributabeWar();
    }

    @Test
    @OperateOnDeployment("dep.active-1")
    public void test1(@ArquillianResource URL contextPath) throws ClientProtocolException, IOException, URISyntaxException {
        DefaultHttpClient client = new DefaultHttpClient();
        try {
            HttpResponse response = client.execute(new HttpGet(contextPath + "/simple"));
            Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
            Assert.assertEquals(1, Integer.parseInt(response.getFirstHeader("value").getValue()));
            Assert.assertFalse(Boolean.valueOf(response.getFirstHeader("serialized").getValue()));

            sessionId = extractSessionId(response);
            response.getEntity().getContent().close();

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    @OperateOnDeployment("dep.active-2")
    public void test2(@ArquillianResource URL contextPath) throws ClientProtocolException, IOException, URISyntaxException {
        DefaultHttpClient client = new DefaultHttpClient();
        try {
            HttpGet request = new HttpGet(contextPath + "/simple");
            attachSessionId(request);
            HttpResponse response = client.execute(request);
            Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
            Assert.assertEquals(2, Integer.parseInt(response.getFirstHeader("value").getValue()));
            // This won't be true unless we have somewhere to which to replicate
            Assert.assertTrue(Boolean.valueOf(response.getFirstHeader("serialized").getValue()));
            response.getEntity().getContent().close();
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    @OperateOnDeployment("dep.active-3")
    public void test3(@ArquillianResource URL contextPath) throws ClientProtocolException, IOException, URISyntaxException {
        DefaultHttpClient client = new DefaultHttpClient();
        try {
            HttpGet request = new HttpGet(contextPath + "/simple");
            attachSessionId(request);
            HttpResponse response = client.execute(request);
            Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
            Assert.assertEquals(3, Integer.parseInt(response.getFirstHeader("value").getValue()));
            response.getEntity().getContent().close();
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    private void attachSessionId(HttpRequest request) {
        request.addHeader("Cookie", "JSESSIONID=" + sessionId + "; Path=/distributable");
    }

    private String extractSessionId(HttpResponse response) {
        for (Header header : response.getHeaders("Set-Cookie")) {
            for (HeaderElement element : header.getElements()) {
                String name = element.getName();
                if ("JSESSIONID".equalsIgnoreCase(name)) {
                    return element.getValue();
                }
            }
        }
        return null;
    }
}

/*


 * https://documentation.bonitasoft.com/7.4?page=bpm-api
 *
 *
 * Copyright (c) 2009-2010 Shanti Subramanyam, Akara Sucharitakul

 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */
package com.testnscale.corehttp;

import javax.script.ScriptEngineManager;
import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.sun.faban.common.NameValuePair;
import com.sun.faban.common.Utilities;
import com.sun.faban.driver.*;
import com.sun.faban.driver.transport.hc3.ApacheHC3Transport;
import com.sun.faban.driver.util.ContentSizeStats;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.Part;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
/**
 * Basic web workload driver drives only one operation via a get request.
 */
@BenchmarkDefinition (
        name    = "Bonita Workload",
        version = "0.1",
        metric  = "req/s"

)
@BenchmarkDriver (
        name             = "bonita",
        threadPerScale   = (float)1,
        opsUnit          = "requests",
        metric           = "req/s",
        responseTimeUnit = TimeUnit.MILLISECONDS

)
@FixedTime (
        cycleType = CycleType.THINKTIME,
        cycleTime = 0,
        cycleDeviation = 5
)
public class BonitaRestDriver {
    public static Boolean initialized = false;
    private DriverContext ctx;
    private HttpTransport http;
    private String url;
    ContentSizeStats contentStats = null;
    Logger logger;

    private ScriptEngine engine;
    Map<String, String> httpHeaders;
    public String barFilePath;
    public Integer userId;
    public String tenantName;


    /**
     * Constructs the basic web workload driver.
     * @throws XPathExpressionException An XPath error occurred
     */
    public BonitaRestDriver() throws XPathExpressionException, ConfigurationException {
        // Corehttp init
        ctx = DriverContext.getContext();
        logger = ctx.getLogger();
        HttpTransport.setProvider(
                "com.sun.faban.driver.transport.hc3.ApacheHC3Transport");
        http = HttpTransport.newInstance();
        StringBuilder urlBuilder = new StringBuilder();

        String s = ctx.getProperty("secure");
        if ("true".equalsIgnoreCase(s))
            urlBuilder.append("https://");
        else
            urlBuilder.append("http://");

        s = ctx.getXPathValue("/bonita/webServer/fa:hostConfig/fa:hostPorts");
        List<NameValuePair<Integer>> hostPorts =
                Utilities.parseHostPorts(s);
        // We currently only support a single host/port with this workload
        logger.info(String.format("hostPorts : %s - Size : %d", s, hostPorts.size()));
        if (hostPorts.size() != 1)
            throw new ConfigurationException(
                    "Only single host:port currently supported.");
        NameValuePair<Integer> hostPort = hostPorts.get(0);
        urlBuilder.append(hostPort.name);
        if (hostPort.value != null)
            urlBuilder.append(':').append(hostPort.value);

        s = ctx.getProperty("contextPath");
        if (s.charAt(0) == '/')
            urlBuilder.append(s);
        else
            urlBuilder.append('/').append(s);
        url = urlBuilder.toString();
        contentStats = new ContentSizeStats(ctx.getOperationCount());
        ctx.attachMetrics(contentStats);

        // Initialize JS script engine for JSON parsing
        ScriptEngineManager sem = new ScriptEngineManager();
        this.engine = sem.getEngineByName("javascript");

        // Retrieve BAR file path
        barFilePath = ctx.getProperty("barFilePath");

        // Retrieve tenant name
        tenantName = ctx.getProperty("tenant");

    }

    protected Map jsonToMap(String json) throws ScriptException {

        String script = "Java.asJSONCompatible(" + json + ")";
        Object result = null;
        result = this.engine.eval(script);

        return (Map) result;
    }

    protected Map jsonToMap(String json, Integer index) throws ScriptException {

        String script = "Java.asJSONCompatible(" + json + ")";
        List result = null;
        result = (List) this.engine.eval(script);
        if (result.size() > 0) {
            return (Map) result.get(index);
        } else
        {
            return null;
        }
    }

    protected List jsonToListMap(String json) throws ScriptException {

        String script = "Java.asJSONCompatible(" + json + ")";
        List result = null;

        return (List) this.engine.eval(script);
    }


    public Map<String, String> login(String user, String password, Integer tenantId) throws IOException, ScriptException {
        logger.info(String.format("Logging to Bonita with (%s:%s)", user, password));
        StringBuilder response;
        if (tenantId == null) {
            response = http.fetchURL(url + String.format("bonita/loginservice?username=%s&password=%s&redirect=false", user, password), "");
        } else {
            response = http.fetchURL(url + String.format("bonita/loginservice?username=%s&password=%s&tenant=%d&redirect=false", user, password, tenantId), "");
        }

        logger.info(String.format("POST Launched login install response = %d - %s - %s", http.getResponseCode(), http.getResponseBuffer(), http.dumpResponseHeaders()));
        if (http.getResponseCode() != 200) {
            return null;
        }
        String[] csrf_token = http.getCookieValuesByName("X-Bonita-API-Token");
        logger.info("Retrieved X-Bonita-API-Token : " + csrf_token[0]);
        //""" {"userName":"user1","password":"user1","firstname":"user1","lastname":"plop", "enabled": "true"} """
        Map<String, String> headers = new HashMap<String, String>();

        headers.put("X-Bonita-API-Token", csrf_token[0]);
        headers.put("Content-Type", "application/json");
        userId = getUserId(user);

        return headers;
    }

    public Map<String, String> platformLogin(String user, String password) throws IOException {
        logger.info(String.format("Platform logging to Bonita with (%s:%s)", user, password));
        StringBuilder response;
        response = http.fetchURL(url + String.format("bonita/platformloginservice?username=%s&password=%s&redirect=false", user, password), "");

        logger.info(String.format("POST Launched platform login install response = %d - %s - %s", http.getResponseCode(), http.getResponseBuffer(), http.dumpResponseHeaders()));
        if (http.getResponseCode() != 200) {
            return null;
        }
        String[] csrf_token = http.getCookieValuesByName("X-Bonita-API-Token");
        logger.info("Retrieved X-Bonita-API-Token : " + csrf_token[0]);
        //""" {"userName":"user1","password":"user1","firstname":"user1","lastname":"plop", "enabled": "true"} """
        Map<String, String> headers = new HashMap<String, String>();


        headers.put("X-Bonita-API-Token", csrf_token[0]);
        headers.put("Content-Type", "application/json");

        return headers;
    }




    public void logout() throws IOException {
        // Log out
        http.fetchURL(url + "bonita/logoutservice");
    }

    public Integer getUserId(String name) throws IOException, ScriptException {
        StringBuilder response;

        response = http.fetchURL(url + "bonita/API/identity/user?f=userName=" + name);
        logger.info(String.format("GET user = %d - %s - %s", http.getResponseCode(), http.getResponseBuffer(), http.dumpResponseHeaders()));
        if (http.getResponseCode() == 200) {
            Map contents = jsonToMap(response.toString(), 0);
            if (contents != null) {
                Integer userId = Integer.parseInt((String) contents.get("id"));
                return userId;
            }
        }
        return null;
    }

    public Integer getTenantId(String name) throws IOException, ScriptException {
        Map<String, String> headers = platformLogin("platformAdmin", "platform");

        StringBuilder response;
        //response = http.fetchURL(url + String.format("bonita/API/platform/tenant?f=name=%s", name));
        response = http.fetchURL(url + "bonita/API/platform/tenant?p=0");
        logger.info(String.format("GET tenant = %d - %s - %s", http.getResponseCode(), http.getResponseBuffer(), http.dumpResponseHeaders()));
        if (http.getResponseCode() == 200) {
            logger.info("Tenants : " + response.toString());
            List listContents = jsonToListMap(response.toString());
            for (int i = 0; i < listContents.size(); i++) {
                Map m = (Map) listContents.get(i);
                String found = (String) m.get("name");
                if (found.equals(name)) {
                    Integer id = Integer.parseInt((String) m.get("id"));
                    return id;
                }
            }
        }

        return null;
    }


    public Integer initializeTenant(String tenant) throws IOException, ScriptException {
        logger.info("Initializing tenant" + tenant);

        StringBuilder response;
        Map<String, String> headers = platformLogin("platformAdmin", "platform");

        // Create tenant
        response = http.fetchURL(url + "bonita/API/platform/tenant", String.format("{\"username\":\"tenantAdmin\",\"password\":\"password\",\"name\":\"%s\"}", tenant), headers);
        logger.info(String.format("POST bonita/API/platform/tenant response = %d - %s - %s", http.getResponseCode(), http.getResponseBuffer(), http.dumpResponseHeaders()));
        Map contents = jsonToMap(response.toString());
        Integer tenantId = Integer.parseInt((String) contents.get("id"));
        logger.info("Tenant created with id " + tenantId.toString());
        // activate process
        response = ((ApacheHC3Transport) http).putURL(url + String.format("bonita/API/platform/tenant/%s",tenantId.toString()), "{\"state\":\"ACTIVATED\"}".getBytes(Charset.forName("UTF-8")), headers);

        logger.info(String.format("PUT tenant on %s = %d - %s - %s", tenantId.toString(), http.getResponseCode(), http.getResponseBuffer(), http.dumpResponseHeaders()));
        return tenantId;
    }

    public void initializeBonita(Integer tenantId) throws IOException, ScriptException {
        logger.info("Initializing Bonita");

        Map<String, String> headers = null;
        if (tenantId == null) {
            headers = login("install", "install", null);
        } else {
            headers = login("tenantAdmin", "password", tenantId);
        }

        // Create user
        StringBuilder response;
        response = http.fetchURL(url + "bonita/API/identity/user/", "{\"userName\":\"user1\",\"password\":\"user1\",\"firstname\":\"user1\",\"lastname\":\"plop\", \"enabled\": \"true\"}", headers);
        logger.info(String.format("POST user1 response = %d - %s - %s", http.getResponseCode(), http.getResponseBuffer(), http.dumpResponseHeaders()));
        userId = Integer.parseInt((String) jsonToMap(response.toString()).get("id"));
        logger.info("userId="+userId.toString());

        // Assign profile 1 to user1
        response = http.fetchURL(url + "bonita/API/userXP/profileMember/", "{\"profile_id\":1,\"member_type\":\"USER\",\"user_id\": 1}", headers);
        logger.info(String.format("POST profile 1 to user 1 response = %d - %s - %s", http.getResponseCode(), http.getResponseBuffer(), http.dumpResponseHeaders()));

        // Assign profile 2 to user1
        response = http.fetchURL(url + "bonita/API/userXP/profileMember/", "{\"profile_id\":2,\"member_type\":\"USER\",\"user_id\": 1}", headers);
        logger.info(String.format("POST profile 2 to user1 response = %d - %s - %s", http.getResponseCode(), http.getResponseBuffer(), http.dumpResponseHeaders()));

        logout();
        headers = login("user1","user1", tenantId);


        File model = new File(barFilePath);
        // Upload process
        FilePart process = new FilePart("fileupload", model);
        List<Part> parts = new ArrayList<Part>();
        parts.add(process);

        StringBuilder deployDef = ((ApacheHC3Transport) http).fetchURL(url + "bonita/portal/processUpload", parts);
        logger.info(String.format("POST process upload response = %d - %s - %s", http.getResponseCode(), http.getResponseBuffer(), http.dumpResponseHeaders()));

        String filename = http.getResponseBuffer().toString();

        // post load process
        response = http.fetchURL(url + "bonita/API/bpm/process", String.format("{ \"fileupload\": \"%s\" }", filename), headers);
        logger.info(String.format("POST deploy %s = %d - %s - %s", filename, http.getResponseCode(), http.getResponseBuffer(), http.dumpResponseHeaders()));
        String processId = (String) jsonToMap(response.toString()).get("id");
        logger.info("processId="+processId);


        // get actor
        //HashMap<String, String> param = new HashMap<String, String>();
        //param.put("f", "process_id=%s".format(processId));
        response = http.fetchURL(url + String.format("bonita/API/bpm/actor?f=process_id=%s",processId));
        logger.info(String.format("GET actor %s = %d - %s - %s", filename, http.getResponseCode(), http.getResponseBuffer(), http.dumpResponseHeaders()));
        Map contents = jsonToMap(response.toString(),0);
        String actorId = (String) contents.get("id");

        // post actormember
        response = http.fetchURL(url + "bonita/API/bpm/actorMember", String.format("{ \"actor_id\": %s, \"user_id\": %s}", actorId, userId), headers);
        logger.info(String.format("POST actorMember %s = %d - %s - %s", filename, http.getResponseCode(), http.getResponseBuffer(), http.dumpResponseHeaders()));

        // activate process
        response = ((ApacheHC3Transport) http).putURL(url + String.format("bonita/API/bpm/process/%s",processId), "{\"activationState\":\"ENABLED\"}".getBytes(Charset.forName("UTF-8")), headers);
        logger.info(String.format("PUT processdef on %s = %d - %s - %s", filename, http.getResponseCode(), http.getResponseBuffer(), http.dumpResponseHeaders()));

    }

    public void launchProcess() throws ScriptException, IOException {
        StringBuilder response;
        // get case id
        response = http.fetchURL(url + String.format("bonita/API/bpm/process?p=0&c=10&o=displayName%%20ASC&f=user_id=%s", userId));
        logger.info(String.format("GET caseid = %d - %s - %s", http.getResponseCode(), http.getResponseBuffer(), http.dumpResponseHeaders()));
        Map contents = jsonToMap(response.toString(),0);
        String caseId = (String) contents.get("id");

        // POST start case
        response = http.fetchURL(url + "bonita/API/bpm/case/", String.format("{ \"processDefinitionId\": %s}", caseId), httpHeaders);
        logger.info(String.format("POST start case = %d - %s - %s", http.getResponseCode(), http.getResponseBuffer(), http.dumpResponseHeaders()));

    }

    /**
     * Do the request.
     * @throws IOException An I/O or network error occurred.
     * @throws ScriptException JSON processing error  *
     */
    @BenchmarkOperation (
            name    = "Request",
            max90th = 250, // 250 millisec
            timing  = Timing.AUTO
    )
    public void doRequest() throws IOException, ScriptException {
        if (initialized == true)
            return;
        initialized = true;
        // 7.5 http://localhost:80/bonita/API/system/session/unusedId
        // 7.4
        Map<String, String> headers = null;
        Integer tenantId = null;

        if (tenantName == "") {
            logger.info("Beginning test for default tenant.");
            headers = login("user1", "user1", null);
        } else {
            logger.info(String.format("Beginning test for tenant %s.",tenantName));
            tenantId = getTenantId(tenantName);
            if (tenantId != null) {
                logger.info(String.format("Tenant %s found with id %d", tenantName, tenantId));
                headers = login("user1", "user1", tenantId);
            } else {
                logger.info(String.format("Tenant %s not found. Initializing.", tenantName));
                tenantId = initializeTenant(tenantName);
                logger.info(String.format("Tenant %s initialized with id %d.", tenantName, tenantId));
            }
        }

        if (headers == null)
            initializeBonita(tenantId);
        headers = login("user1","user1", tenantId);
        httpHeaders = headers;
        launchProcess();

        //if (ctx.isTxSteadyState())
        //    contentStats.sumContentSize[ctx.getOperationId()] += size;
    }
}

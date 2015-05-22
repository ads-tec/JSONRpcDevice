/**
 * JSonRpcDevice
 * Class to access ads-tec json rpc interface from osgi/java.
 * Default communication path depends on platform where java is running
 * - When running on the target osgi@localhost is used (other credentials may be used when calling setAuthRemoteOnly( false );
 * - When running from within development environment credentials given to authenticate() are used
 * 
 * Example:
 * 			JSONRpcDevice rpc = new JSONRpcDevice();
 *			rpc.authenticate("192.168.0.254", "admin", "admin");  // for access when running within eclipse
 *			// get status value
 *			System.out.println(rpc.statusGet("version"));
 *			// Set config value
 *			int id = rpc.configSessionStart();
 *			Map<String,String> vars = new HashMap<String,String>();
 *			vars.put("system_location", "office");
 *			rpc.configSet(id, vars);
 *			rpc.configSessionCommit(id);
 *
 * @author jgwn/ads-tec
 * @version 1.0
 */

package jsonrpcdevice;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.osgi.framework.BundleContext;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2Session;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2SessionException;

public class JSONRpcDevice
{
	private URL serverCommURL = null;
	private JSONRPC2Session RPCSession = null;
	private String sid = null;
	private String user = null, password = null;
	private int timeout, expires;
	private JSONObject acls;
	private boolean forceAuthentication = false;

	/**
	 * Constructor to create an instance of JSONRpcDevice
	 */
	public JSONRpcDevice() {
		try {
			serverCommURL = new URL("http://127.0.0.1:8000/rpc");
		} catch (MalformedURLException e) {
		}
	}

	@Deprecated
	public JSONRpcDevice(String ip) {
		try {
			this.serverCommURL = new URL("http://" + ip + ":8000/rpc");
		} catch (MalformedURLException e) {
		}
	}

	@Deprecated
	public boolean authenticate() throws JSONRpcException {
		return authenticateLocal();
	}

	@Deprecated
	public boolean authenticateLocal() throws JSONRpcException {

		if (getSid() == null) {
			throw new JSONRpcException("no session ID returned: check username/password");
		}
		return true;
	}

	@Deprecated
	public boolean authenticateRemote(String user, String password) throws JSONRpcException {
		return authenticate(this.serverCommURL, user, password);
	}

	private String createSession() throws JSONRpcException {
		List<Object> params = new ArrayList<Object>();
		params.add("00000000000000000000000000000000");
		params.add("session");
		params.add("create");
		Map<String, String> innerparams = new HashMap<String, String>();
		// authenticate
		innerparams.put("user", this.user);
		innerparams.put("password", this.password);
		params.add(innerparams);

		JSONRPC2Session myAuthSession = new JSONRPC2Session(this.serverCommURL);
		JSONRPC2Request req = new JSONRPC2Request("call", params, "req-1");
		JSONRPC2Response resp = null;

		try {
			resp = myAuthSession.send(req);
		} catch (JSONRPC2SessionException je) {
			throw new JSONRpcException("rpc request failed!", je);
		}

		if (!resp.indicatesSuccess()) {
			throw new JSONRpcException("rpc request error: " + resp.getError().getMessage());
		}

		// make RPCSession object for return
		JSONArray res_array = (JSONArray) JSONValue.parse(resp.getResult().toString());
		JSONObject res_obj = (JSONObject) res_array.get(1);
		if (res_obj.containsKey("error")) {
			throw new JSONRpcException((String) res_obj.get("error"));
		}
		setSid((String) res_obj.get("sid"));
		setExpires((Integer) res_obj.get("expires"));
		setTimeout((Integer) res_obj.get("timeout"));
		setAcls((JSONObject) res_obj.get("acls"));
		return (String) res_obj.get("sid");
	}

	@Deprecated
	public boolean authenticate(String user, String password) throws JSONRpcException {

		if (isRunningOnEmbeddedDevice() && user == null || password == null) {
			return authenticateRemote(user, password);
		}

		return authenticateLocal();
	}

	/**
	 * Call to authenticate is required when connecting to different host
	 * (development process) or when different privileges (then these for user
	 * osgi) are required. When authenticate() is not used rpc connection is
	 * opened to localhost as user "osgi".
	 * 
	 * @param dst_addr
	 *            IP address or hostname to connect to
	 * @param username
	 *            username to use for authentication
	 * @param password
	 *            password to use for authentication
	 * @return Returns true on success
	 * @throws JSONRpcException
	 *             in case of unreachable destination, ...
	 */
	public boolean authenticate(String dst_addr, String username, String password) throws JSONRpcException {
		try {
			return authenticate(new URL("http://" + dst_addr + "/rpc"), username, password);
		} catch (MalformedURLException e) {
			throw new JSONRpcException("url invalid", e);
		}
	}

	/**
	 * Call to authenticate is required when connecting to different host
	 * (development process) or when different privileges (than these for user
	 * osgi) are required. When authenticate() is not used rpc connection is
	 * opened to localhost as user "osgi".
	 * 
	 * @param url
	 *            URL to connect to
	 * @param username
	 *            username to use for authentication
	 * @param password
	 *            password to use for authentication
	 * @return Returns true on success
	 * @throws JSONRpcException
	 *             in case of unreachable destination, ...
	 */
	public boolean authenticate(URL url, String username, String password) throws JSONRpcException {
		if (this.forceAuthentication || !isRunningOnEmbeddedDevice()) {
			this.serverCommURL = url;
			this.user = username;
			this.password = password;
			return createSession() != null;
		} else {
			return true;
		}
	}

	/**
	 * Creates a config session to be able to change configuration parameters
	 * (nvram)
	 * 
	 * @return config session id required for subsequent calls to configSet,
	 *         configAbort, configCommit
	 * @throws JSONRpcException
	 */
	public int configSessionStart() throws JSONRpcException {
		return ((Long) this.doRPC("config", "sess_start", null).get("cfg_session_id")).intValue();
	}

	@Deprecated
	public Object jsonRPConfigSet(int configId, Map<String, String> vars) throws JSONRpcException {
		return this.addObsoleteUbusHeader(this.configSet(configId, vars));
	}

	/**
	 * Writes a parameter to configuration database (nvram)
	 * 
	 * @param configId
	 * @param vars
	 *            Hashmap containing key/value pairs to set
	 * @return result of rpc request
	 * @throws JSONRpcException
	 */
	public JSONObject configSet(int configId, Map<String, String> vars) throws JSONRpcException {
		Map<String, Object> rpc_params = new HashMap<String, Object>();
		rpc_params.put("cfg_session_id", configId);
		rpc_params.put("values", vars);
		return this.doRPC("config", "set", rpc_params);
	}

	/**
	 * Aborts a running config session
	 * 
	 * @param configId
	 *            as returned from configSessionStart()
	 * @return true on success
	 * @throws JSONRpcException
	 */
	public Boolean configSessionAbort(int configId) throws JSONRpcException {
		Map<String, Object> rpc_params = new HashMap<String, Object>();
		rpc_params.put("cfg_session_id", configId);
		try {
			this.doRPC("config", "sess_abort", rpc_params);
			return true;
		} catch (JSONRpcException e) {
			return false;
		}
	}

	/**
	 * Commits a running config session to the system.
	 * 
	 * @param configId
	 *            as returned from configSessionStart() and used by configSet()
	 * @return true on success
	 * @throws JSONRpcException
	 */
	public Boolean configSessionCommit(int configId) throws JSONRpcException {
		Map<String, Object> rpc_params = new HashMap<String, Object>();
		rpc_params.put("cfg_session_id", configId);
		try {
			this.doRPC("config", "sess_commit", rpc_params);
			return true;
		} catch (JSONRpcException e) {
			return false;
		}
	}

	/**
	 * Get parameters from configuration database (nvram)
	 * 
	 * @param List
	 *            of vars to fetch
	 * @return rpc result containing list of requested values
	 * @throws JSONRpcException
	 */
	public JSONObject configGet(List<String> vars) throws JSONRpcException {
		Map<String, Object> rpc_params = new HashMap<String, Object>();
		rpc_params.put("keys", vars);
		return doRPC("config", "get", rpc_params);
	}

	/**
	 * Get single parameter from configuration database (nvram)
	 * 
	 * @param var
	 *            Name of config variable to fetch
	 * @return Value of config variable
	 * @throws JSONRpcException
	 */
	public String configGet(String var) throws JSONRpcException {
		List<String> params = new ArrayList<String>();
		params.add(var);
		return ((JSONObject) ((JSONArray) configGet(params).get("result")).get(0)).get(var).toString();
	}

	@Deprecated
	public JSONArray addObsoleteUbusHeader(JSONObject jsonObj) {
		JSONArray ubusObj = new JSONArray();
		ubusObj.add(0);
		ubusObj.add(jsonObj);
		return ubusObj;
	}

	@Deprecated
	/* re-add ubus header to rpc result to be backward compatible */
	public Object jsonRPC(String method, String action, Map<String, Object> innerparams) throws JSONRpcException {
		return addObsoleteUbusHeader(doRPC(method, action, innerparams));
	}

	/**
	 * Perform generic RPC call. Opens RPC connection if not already open
	 * 
	 * @param object
	 *            Name of the object to talk to
	 * @param method
	 *            Name of the method to call
	 * @param params
	 *            Parameters for the given method
	 * @return
	 * @throws JSONRpcException
	 */
	public JSONObject doRPC(String object, String method, Map<String, Object> params) throws JSONRpcException {
		// Build JSONString
		if (params == null)
			params = new HashMap<String, Object>();

		JSONRPC2Request request = new JSONRPC2Request("call", 1);
		List<Object> rpc_obj = new ArrayList<Object>();
		rpc_obj.add(getSid());
		rpc_obj.add(object);
		rpc_obj.add(method);
		rpc_obj.add(params);
		request.setPositionalParams(rpc_obj);

		try {
			JSONRPC2Response response = RPCSession.send(request);
			if (response.indicatesSuccess() && response.getError() == null) {
				JSONArray result = (JSONArray) response.getResult();
				if ((Long) result.get(0) == 0) {
					if (result.size() == 1)
						return null;
					else
						return (JSONObject) result.get(1);
				} else {
					if (result.size() > 1)
						throw (new JSONRpcException("RPC error " + result.get(0) + ": " + (JSONObject) (result.get(1))));
					else
						throw (new JSONRpcException("RPC error " + result.get(0)));
				}
			} else {
				throw (new JSONRpcException("Could not send JSON RPC: "+response.toString()));
			}
		} catch (Exception je) {
			setSid(null);
			if (je instanceof JSONRpcException)
				throw (JSONRpcException)je;
			else
				throw new JSONRpcException("Could not send JSON RPC: "+je.toString(), je);
		}
	}

	/**
	 * push data via wwh to Big-LinX
	 * 
	 * @param tablename
	 * @param dataset
	 *            [ row1, row2, ..., row_n ] with row_i = [ val1, val2, ...,
	 *            val_m ] where table has m columns
	 * @return returns result of rpc call
	 * @throws JSONRpcException
	 */
	public Object wwhPush(String tablename, List<List<Object>> dataset) throws JSONRpcException {
		Map<String, Object> rpc_params = new HashMap<String, Object>();
		rpc_params.put("table", tablename);
		rpc_params.put("data", dataset);
		return this.doRPC("wwh", "push", rpc_params);
	}

	@Deprecated
	public Object jsonRPCConfigGet(List<String> vars) throws JSONRpcException {
		return addObsoleteUbusHeader(configGet(vars));
	}

	@Deprecated
	public Object jsonRPCStatusGet(String function, List<String> params) throws JSONRpcException {
		return addObsoleteUbusHeader(statusGet(function, params));
	}

	/**
	 * Perform status call
	 * 
	 * @param function
	 *            Function to call
	 * @param params
	 *            Parameters for called function as list
	 * @return return value of status call
	 * @throws JSONRpcException
	 */

	public JSONObject statusGet(String function, List<String> params) throws JSONRpcException {
		Map<String, Object> rpc_params = new HashMap<String, Object>();
		rpc_params.put("function", function);
		rpc_params.put("parameters", params);
		return doRPC("status", "get", rpc_params);
	}

	/**
	 * Perform status call
	 * 
	 * @param function
	 *            Function to call
	 * @param param1
	 *            First parameter for status call
	 * @param param2
	 *            Second parameter for status call
	 * @return return value of status call
	 * @throws JSONRpcException
	 */
	public String statusGet(String function, String param1, String param2) throws JSONRpcException {
		List<String> params = new ArrayList<String>();
		params.add(param1);
		params.add(param2);
		return this.statusGet(function, params).get(function).toString();
	}

	/**
	 * Perform status call with no parameters
	 * 
	 * @param function
	 *            Function to call
	 * @return return value of status call
	 * @throws JSONRpcException
	 */
	public String statusGet(String function) throws JSONRpcException {
		return this.statusGet(function, "", "");
	}

	/**
	 * Get value of given GPIO
	 * 
	 * @param gpio
	 *            Name of GPIO to query
	 * @return Value of the requested GPIO
	 * @throws JSONRpcException
	 */
	public String getGpio(String gpio) throws JSONRpcException {
		Map<String, Object> innerparams = new HashMap<String, Object>();
		innerparams.put("signal", gpio);
		return doRPC("gpio", "get", innerparams).get(gpio).toString();
	}

	/**
	 * Set GPIO on
	 * 
	 * @param gpio
	 * @throws JSONRpcException
	 */
	public void setGpioOn(String gpio) throws JSONRpcException {
		Map<String, Object> innerparams = new HashMap<String, Object>();
		innerparams.put("signal", gpio);
		doRPC("gpio", "on", innerparams);
	}

	/**
	 * Set GPIO off
	 * 
	 * @param gpio
	 * @throws JSONRpcException
	 */
	public void setGpioOff(String gpio) throws JSONRpcException {
		Map<String, Object> innerparams = new HashMap<String, Object>();
		innerparams.put("signal", gpio);
		doRPC("gpio", "off", innerparams);
	}

	@Deprecated
	public Object jsonRPCTableGet(String tableName, HashMap<String, String> conditions) throws JSONRpcException {
		return this.addObsoleteUbusHeader(this.configTableGet(tableName, conditions));
	}

	/**
	 * Get table from configuration database (nvram)
	 * 
	 * @param tableName
	 * @param conditions
	 *            Maybe { <colname1>=<value1>, <colname2>=<value2> }, up to two
	 *            conditions are accepted. May be null.
	 * @return the requested data
	 * @throws JSONRpcException
	 */
	public JSONObject configTableGet(String tableName, HashMap<String, String> conditions) throws JSONRpcException {
		Map<String, Object> rpc_params = new HashMap<String, Object>();
		rpc_params.put("tablename", tableName);
		if (conditions != null)
			rpc_params.put("condition", conditions);
		return this.doRPC("config", "table_get", rpc_params);
	}

	public JSONObject getAcls() {
		return acls;
	}

	private void setAcls(JSONObject acls) {
		this.acls = acls;
	}

	public int getExpires() {
		return expires;
	}

	private void setExpires(int expires) {
		this.expires = expires;
	}

	public int getTimeout() {
		return timeout;
	}

	private void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public static boolean isRunningOnEmbeddedDevice(BundleContext bundleContext) throws JSONRpcException {
		return isRunningOnEmbeddedDevice();
	}

	public static boolean isRunningOnEmbeddedDevice() throws JSONRpcException {
		if (System.getProperty("adstec.embedded") != null && System.getProperty("adstec.embedded").equals("true")) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * 
	 * @return Returns the RPC session ID being used. Opens a new connection of
	 *         not connected yet.
	 * @throws JSONRpcException
	 */
	public String getSid() throws JSONRpcException {
		if (this.RPCSession == null) {
			RPCSession = new JSONRPC2Session(serverCommURL);
		}

		if (this.sid == null && this.serverCommURL != null && this.user != null && this.password != null) {
			setSid((String) createSession());
			return this.sid;
		} else if (sid == null && isRunningOnEmbeddedDevice()) {
			try {
				JSONObject adstecSession = (JSONObject) JSONValue.parse(System.getProperty("adstec.session"));
				setExpires((Integer) adstecSession.get("expires"));
				setTimeout((Integer) adstecSession.get("timeout"));
				setAcls((JSONObject) adstecSession.get("acls"));
				setSid((String) adstecSession.get("sid"));
				return this.sid;
			} catch (Exception e) {
				throw new JSONRpcException("Unable to get static session ID");
			}
		} else
			return this.sid;
	}

	private void setSid(String sid) {
		this.sid = sid;
	}

	/**
	 * Sometimes it's required to use authentication even if running directly on
	 * the target
	 * 
	 * @param value
	 */
	public void forceLocalAuthentication(boolean value)
	/*
	 * Perform Authentication only if running on remote device (for debugging)
	 * and use local RPC-Session otherwise
	 */
	{
		this.forceAuthentication = value;
	}
}

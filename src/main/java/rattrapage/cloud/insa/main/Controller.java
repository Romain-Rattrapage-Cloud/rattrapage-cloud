package rattrapage.cloud.insa.main;


import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.Random;

import javax.annotation.PostConstruct;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;


@RestController
public class Controller {

	static private String staticIpServerList = "http://cacaacac.alwaysdata.net/rattrapage-cloud/servers.json";
	static private String port = "8080";
	static private String[] loginServers = {"http://localhost:8080", "http://localhost:8080", "http://localhost:8080"};
	static private String hostServer = "http://localhost:8080";
	static private String databaseServer = "http://localhost:8080";
	static private String databaseLocation = "src/main/resources/static/dtbase.json";
	static private int serverNumber = 1;

    private static void changeServer(long duration, int timeout){
    	if (duration > timeout)
        {
        	serverNumber++;
        	serverNumber %= loginServers.length; 
        }
    }
    
    private static JSONArray getDatabase(){
    	JSONArray dtbaseJson = new JSONArray();
    	
		RestTemplate restTemplate = new RestTemplate();
		String dtbaseString = restTemplate.getForObject(databaseServer + "/db", String.class);
		
		int beginIndex=dtbaseString.indexOf("{");
		int endIndex = dtbaseString.indexOf("}"); 
		while (endIndex != -1) {
			dtbaseJson.put(new JSONObject(dtbaseString.substring(beginIndex, endIndex+1)));
			beginIndex 	= dtbaseString.indexOf("{", endIndex+1);
			endIndex	= dtbaseString.indexOf("}", endIndex+1);
		}
    	return dtbaseJson;
    }
    
    private static void setDatabase(JSONArray toWrite){
    	try {
    		PrintWriter out = new PrintWriter(databaseLocation);
	    	out.println(toWrite.toString());
	    	out.close();
    	} catch (FileNotFoundException e) {
    		e.printStackTrace();
		}
    }
    
    private static void addDatabase(JSONObject toWrite){
		JSONArray dtbase = getDatabase();
		dtbase.put(toWrite);
		setDatabase(dtbase);
    }
	
    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String homepage(){
    	String webpage = new String();
    	webpage += "<form action=\"/\" method=\"post\">";
    	webpage += "<label for=\"id\">ID:</label><br>";
    	webpage += "<input type=\"text\" id=\"id\" name=\"id\"><br>";
    	webpage += "<label for=\"pwd\">Password:</label><br>";
    	webpage += "<input type=\"text\" id=\"pwd\" name=\"pwd\"><br>";
    	webpage += "<button type=\"submit\" name=\"action\" value=\"register\">Register</button>";
    	webpage += "<button type=\"submit\" name=\"action\" value=\"login\">Log in</button>";
    	webpage += "</form>";
    	return webpage;
    }	
    
    @RequestMapping(path = "/", method = RequestMethod.POST, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String form(@RequestBody MultiValueMap<String, String> formParameters) {
        String action 	= formParameters.getFirst("action");
        String id 		= formParameters.getFirst("id");
        String pwd 		= formParameters.getFirst("pwd");
        
        JSONObject json = new JSONObject();
    	json.put("id", id);
    	json.put("pwd", pwd);
    	
    	RestTemplate restTemplate = new RestTemplate();
    	String result = new String();
    	if ( Objects.equals(action, "register") ){
    		result = restTemplate.postForObject(databaseServer + "/register", json.toString(), String.class);
    	} else if ( Objects.equals(action, "login") ) {
    		long time = System.currentTimeMillis();
    		result = restTemplate.postForObject(loginServers[serverNumber] + "/login/" + serverNumber,	  json.toString(), String.class);
    		time = System.currentTimeMillis() - time;
    		changeServer(time, 500);
    	} else {
    		result = action;
    	}
    	return result;
    }
    
    @RequestMapping(value = "/register", method = RequestMethod.POST)
    private static String register(@RequestBody String body){
    	addDatabase(new JSONObject(body));
    	
    	return "<meta http-equiv=\"refresh\" content=\"1; URL=\"" + hostServer + "\"/>";
    }
    
    @RequestMapping(value = "/login/{serverNb}", method = RequestMethod.POST)
    private static String login(@RequestBody String body, @PathVariable int serverNb){
    	Random rand = new Random();
    	try {
			Thread.sleep(rand.nextInt(1000));
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    	
    	JSONObject json = new JSONObject(body);
    	JSONArray dtbase = getDatabase();
    	
    	RestTemplate restTemplate = new RestTemplate();
    	String result;
    	    	
    	boolean found = false;
    	for (int i = 0; i < dtbase.length(); i++)
    	    if ( !(dtbase.isNull(i)) && (dtbase.get(i).toString().compareTo(json.toString()) == 0) )
    	        found = true;
    	
    	if (found){
    		result = "<h1>You are connected to the server " + (serverNb + 1) + ".</h1><br>";
    	} else {
			result = "<h1>Wrong ID or password.</h1><br>";
    	}
    	result += restTemplate.getForObject(hostServer, String.class);
    	return result;
    }

    @RequestMapping(value = "/db", method = RequestMethod.GET)
    public String db(){
    	BufferedReader in;
    	String dtbaseString = new String();
		try {
			in = new BufferedReader(new FileReader(databaseLocation));
			dtbaseString = in.readLine(); 
			in.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
    	return dtbaseString;
    }	

    @PostConstruct
    void getServers(){
    	RestTemplate restTemplate = new RestTemplate();
		String servers = restTemplate.getForObject(staticIpServerList, String.class);
        
		JSONObject json = new JSONObject(servers);
        hostServer = "http://" + json.get("host").toString() + ":" + port;
        databaseServer = "http://" + json.get("database").toString() + ":" + port;
        loginServers[0] = "http://" + json.get("login1").toString() + ":" + port;
        loginServers[1] = "http://" + json.get("login2").toString() + ":" + port;
        loginServers[2] = "http://" + json.get("login3").toString() + ":" + port;
    }
}

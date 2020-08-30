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
	
	//	fixed server and port to access the others
	static private String staticIpServerList = "http://cacaacac.alwaysdata.net/rattrapage-cloud/servers.json";
	static private String port = "8080";
	
	//	temporary servers to ensure the server can run even if the staticIpServerList above is missing or unreachable
	static private String[] loginServers = {"http://localhost:8080", "http://localhost:8080", "http://localhost:8080"};
	static private String hostServer = "http://localhost:8080";
	static private String databaseServer = "http://localhost:8080";
	
	//	private location of the database for the database server
	static private String databaseLocation = "src/main/resources/static/dtbase.json";
	
	//	increment to switch from a login server to another one with loginServers
	static private int serverNumber = 1;

	//	function to switch from a login server to another, used by the host server
    private static void changeServer(long duration, int timeout){
    	if (duration > timeout)
        {
        	serverNumber++;
        	serverNumber %= loginServers.length; 
        }
    }
    
    //	function to read the database and convert it to a JSONArray, used by the database server
    private static JSONArray getDatabase(){
    	JSONArray dtbaseJson = new JSONArray();
    	
		RestTemplate restTemplate = new RestTemplate();
		String dtbaseString = restTemplate.getForObject(databaseServer + "/db", String.class);
		
		//	the JSONArray library can not parse strings, so it is done manually
		int beginIndex=dtbaseString.indexOf("{");
		int endIndex = dtbaseString.indexOf("}"); 
		while (endIndex != -1) {
			// the JSONObject library can parse a JSON object however
			dtbaseJson.put(new JSONObject(dtbaseString.substring(beginIndex, endIndex+1)));
			beginIndex 	= dtbaseString.indexOf("{", endIndex+1);
			endIndex	= dtbaseString.indexOf("}", endIndex+1);
		}
    	return dtbaseJson;
    }
    
    //	function to write the database, used by the database server
    private static void setDatabase(JSONArray toWrite){
    	try {
    		PrintWriter out = new PrintWriter(databaseLocation);
	    	out.println(toWrite.toString());
	    	out.close();
	    //	debug if the file can not be created, should not happen
    	} catch (FileNotFoundException e) {
    		e.printStackTrace();
		}
    }
    
    //	function to add a JSON object to the database, used by the database server
    private static void addDatabase(JSONObject toWrite){
		JSONArray dtbase = getDatabase();
		dtbase.put(toWrite);
		setDatabase(dtbase);
    }
	
    //	web service displaying a form, used by the host server
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
    
    //	web service to translate APPLICATION_FORM_URLENCODED_VALUE from the web service above to JSON,
    //	call the login or database servers depending on the form, used by the host server
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
    	//	check if the register button have been clicked on
    	if ( Objects.equals(action, "register") ){
    		//	call the register database server
    		result = restTemplate.postForObject(databaseServer + "/register", json.toString(), String.class);
    	//	check if the login button have been clicked on
    	} else if ( Objects.equals(action, "login") ) {
    		//	start a timer
    		long time = System.currentTimeMillis();
    		//	call one of the login servers
    		result = restTemplate.postForObject(loginServers[serverNumber] + "/login/" + serverNumber,	  json.toString(), String.class);
    		//	stop the timer and change server if the time is above 500 ms
    		time = System.currentTimeMillis() - time;
    		changeServer(time, 500);
    	} else {
    		// debug if neither button have been called, should not happen
    		result = action;
    	}
    	return result;
    }
    
    //	web service to register a new pair of id, password, used by the database server
    @RequestMapping(value = "/register", method = RequestMethod.POST)
    private static String register(@RequestBody String body){
    	addDatabase(new JSONObject(body));
    	//	call back the host server after execution
    	return "<meta http-equiv=\"refresh\" content=\"1; URL=\"" + hostServer + "\"/>";
    }
    
    //	web service to check if the login is correct, used by the login servers
    @RequestMapping(value = "/login/{serverNb}", method = RequestMethod.POST)
    private static String login(@RequestBody String body, @PathVariable int serverNb){
    	// wait for 0 to 1000 ms to simulate heavy traffic
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
    	
    	// check if the pair id, password if found in the database
    	boolean found = false;
    	for (int i = 0; i < dtbase.length(); i++)
    	    if ( !(dtbase.isNull(i)) && (dtbase.get(i).toString().compareTo(json.toString()) == 0) )
    	        found = true;
    	
    	if (found){
    		//	display a connection and show the login server number by giving it in the url
    		result = "<h1>You are connected to the server " + (serverNb + 1) + ".</h1><br>";
    	} else {
    		// 	display a message if not found
			result = "<h1>Wrong ID or password.</h1><br>";
    	}
    	// show below the host server form
    	result += restTemplate.getForObject(hostServer, String.class);
    	return result;
    }

    //	web service to give the database in response, used by the database server
    @RequestMapping(value = "/db", method = RequestMethod.GET)
    public String db(){
    	BufferedReader in;
    	String dtbaseString = new String();
		try {
			in = new BufferedReader(new FileReader(databaseLocation));
			dtbaseString = in.readLine(); 
			in.close();
		//	debug if the file is unreachable, should not happen
		} catch (Exception e) {
			e.printStackTrace();
		}
    	return dtbaseString;
    }	
    
    //	startup call of the server to get public IP of the others, used by all the servers
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

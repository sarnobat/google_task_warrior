import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.*;

public class Test {
	public static void main(String[] args) {
		HttpTransport httpTransport = new NetHttpTransport();
		JacksonFactory jsonFactory = new JacksonFactory();

		// The clientId and clientSecret can be found in Google Developers Console
		// https://cloud.google.com/console/project
	    String clientId = "319518489594.apps.googleusercontent.com";
	    String clientSecret = "YOUR_CLIENT_SECRET";
	}
}

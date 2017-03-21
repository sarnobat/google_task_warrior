
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

/**
 * groovy list_display_2017.groovy | tee list_display_2017.txt | column -t
 * -s$'\t'
 * 
 * @author sarnobat@google.com (Your Name Here)
 * 
 * Outputs eid and FULL event summary directly from GMail
 * 
 */
public class ListDisplay2017 {

	/**
	 * Build and return an authorized Calendar client service.
	 * 
	 * @return an authorized Calendar client service
	 * @throws IOException
	 */
	public static com.google.api.services.calendar.Calendar getCalendarService() throws IOException {

		/** Application name. */
		String APPLICATION_NAME = "Google Calendar API Java Quickstart";

		/** Directory to store user credentials for this application. */
		java.io.File DATA_STORE_DIR = new java.io.File(System.getProperty("user.home"),
				".credentials/calendar-java-quickstart");

		/** Global instance of the {@link FileDataStoreFactory}. */
		@Deprecated
		FileDataStoreFactory DATA_STORE_FACTORY;

		/** Global instance of the JSON factory. */
		JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

		/** Global instance of the HTTP transport. */
		@Deprecated
		HttpTransport HTTP_TRANSPORT;

		/**
		 * Global instance of the scopes required by this quickstart.
		 * 
		 * If modifying these scopes, delete your previously saved credentials
		 * at ~/.credentials/calendar-java-quickstart
		 */
		List<String> SCOPES = Arrays.asList(CalendarScopes.CALENDAR_READONLY);
		try {
			HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
			DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
		} catch (Throwable t) {
			t.printStackTrace();
			System.exit(1);
			return null;
		}
		// Load client secrets.
		InputStream ipn =
		// Quickstart.class.getResourceAsStream("/client_secret.json");
		new FileInputStream(
				"/sarnobat.garagebandbroken/trash/google-api-java-client-samples/calendar-cmdline-sample/client_secret_319518489594-lp4c78he5o88q0ga62o08j94pevnbrlo.apps.googleusercontent.com.json");
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
				new InputStreamReader(ipn));

		// Build flow and trigger user authorization request.
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT,
				JSON_FACTORY, clientSecrets, SCOPES).setDataStoreFactory(DATA_STORE_FACTORY)
				.setAccessType("offline").build();
		Credential credential1 = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver())
				.authorize("user");
		System.out.println("Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());
		Credential credential = credential1;
		return new com.google.api.services.calendar.Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY,
				credential).setApplicationName(APPLICATION_NAME).build();
	}

	public static void main(String[] args) throws IOException {

		// Build a new authorized API client service.
		// Note: Do not confuse this class with the
		// com.google.api.services.calendar.model.Calendar class.
		com.google.api.services.calendar.Calendar service = getCalendarService();

		// List the next 10 events from the primary calendar.
		DateTime now = new DateTime(System.currentTimeMillis());
		Events events = service.events().list("primary").setMaxResults(10).setTimeMin(now)
				.setOrderBy("startTime").setSingleEvents(true).execute();
		List<Event> items = events.getItems();
		if (items.size() == 0) {
			System.out.println("No upcoming events found.");
		} else {
			System.out.println("Upcoming events");
			for (Event event : items) {
				DateTime start = event.getStart().getDateTime();
				if (start == null) {
					start = event.getStart().getDate();
				}
				URI uri;
				try {
					uri = new URI(event.getHtmlLink());
				} catch (URISyntaxException e) {
					e.printStackTrace();
					throw new RuntimeException(e);
				}
				List<NameValuePair> n = URLEncodedUtils.parse(uri, "UTF-8");
				Map<String, String> m = new HashMap<String, String>();
				for (NameValuePair p : n) {
					m.put(p.getName(), p.getValue());
				}
				String eventId = m.get("eid");
				System.out.printf("%s\t%s\t%s\t%s\t%s\t%s\t%s\n", start, eventId,
						event.getHtmlLink(), event.getEtag(), event.getId(), event.getICalUID(),
						event.getSummary());
			}
		}
	}

}

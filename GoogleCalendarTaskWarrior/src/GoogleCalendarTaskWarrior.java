import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.CalendarListEntry;

public class GoogleCalendarTaskWarrior {

	private static Calendar client;

	public static void main(String[] args) throws IOException {
		client = getCalendarService();
		@SuppressWarnings("unchecked")
		List<CalendarListEntry> allCalendars = (List<CalendarListEntry>) client
				.calendarList().list().execute().get("items");
		for (CalendarListEntry aCalendar : allCalendars) {
			System.out.println(aCalendar.getSummary() + "::"
					+ aCalendar.getId() + "::" + aCalendar.getClass() + "::"
					+ aCalendar);
		}
	}

	private static Calendar getCalendarService() {
		final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

		try {
			HttpTransport httpTransport = GoogleNetHttpTransport
					.newTrustedTransport();
			FileDataStoreFactory dataStoreFactory = new FileDataStoreFactory(
					new java.io.File(
							System.getProperty("user.home"), ".store/calendar_sample"));

			Credential credential;
			{
				{
					// load client secrets
					GoogleClientSecrets clientSecrets = GoogleClientSecrets
							.load(JSON_FACTORY,
									new InputStreamReader(
											GoogleCalendarTaskWarrior.class
													.getResourceAsStream("/client_secrets.json")));
					if (clientSecrets.getDetails().getClientId()
							.startsWith("Enter")
							|| clientSecrets.getDetails().getClientSecret()
									.startsWith("Enter ")) {
						System.out
								.println("Overwrite the src/main/resources/client_secrets.json file with the client secrets file "
										+ "you downloaded from the Quickstart tool or manually enter your Client ID and Secret "
										+ "from https://code.google.com/apis/console/?api=calendar#project:319518489594 "
										+ "into src/main/resources/client_secrets.json");
						System.exit(1);
					}

					GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
							httpTransport, JSON_FACTORY, clientSecrets,  ImmutableSet.of(
									CalendarScopes.CALENDAR,
									CalendarScopes.CALENDAR_READONLY))
							.setDataStoreFactory(dataStoreFactory).build();

					credential = new AuthorizationCodeInstalledApp(flow,
							new LocalServerReceiver()).authorize("user");
				}
			}

			client = new Calendar.Builder(httpTransport, JSON_FACTORY,
					credential).setApplicationName("gcal-task-warrior").build();

			return client;

		} catch (IOException e) {
			System.err.println(e.getMessage());
		} catch (Throwable t) {
			t.printStackTrace();
		}
		System.exit(-1);
		return client;
	}

}

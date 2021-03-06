import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.security.GeneralSecurityException;
import java.util.List;

import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.common.collect.ImmutableSet;

public class GoogleCalendarTaskWarrior {

	public static void main(String[] args) throws IOException,
			GeneralSecurityException {
		Calendar client = getCalendarService();

		System.out.println("Getting calendars...");
		@SuppressWarnings("unchecked")
		List<CalendarListEntry> allCalendars = (List<CalendarListEntry>) client
				.calendarList().list().execute().get("items");

		for (CalendarListEntry aCalendar : allCalendars) {

			System.out.println(aCalendar.getSummary() + "::"
					+ URLDecoder.decode(aCalendar.getId(), "UTF-8")
			// + " :: " + aCalendar.toPrettyString()
			// + " :: " + new JSONObject(aCalendar) + "\n"
					);
		}
	}

	/************************************************************************
	 * Boilerplate
	 ************************************************************************/
	
	private static Calendar getCalendarService()
			throws GeneralSecurityException, IOException {
		System.out.println("Authenticating...");

		HttpTransport httpTransport = GoogleNetHttpTransport
				.newTrustedTransport();
		Calendar client = new Calendar.Builder(
				httpTransport,
				JacksonFactory.getDefaultInstance(),
				new AuthorizationCodeInstalledApp(
						new GoogleAuthorizationCodeFlow.Builder(
								httpTransport,
								JacksonFactory.getDefaultInstance(),
								GoogleClientSecrets.load(
										JacksonFactory.getDefaultInstance(),
										new InputStreamReader(
												GoogleCalendarTaskWarrior.class
														.getResourceAsStream("/client_secrets.json"))),
								ImmutableSet.of(CalendarScopes.CALENDAR,
										CalendarScopes.CALENDAR_READONLY))
								.setDataStoreFactory(
										new FileDataStoreFactory(
												new java.io.File(
														System.getProperty("user.home"),
														".store/calendar_sample")))
								.build(), new LocalServerReceiver())
						.authorize("user")).setApplicationName(
				"gcal-task-warrior").build();
		return client;

	}
}

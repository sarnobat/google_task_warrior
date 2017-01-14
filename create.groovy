import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Enumeration;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.MimeMultipart;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.Calendar.Events;
import com.google.api.services.calendar.Calendar.Events.Update;
import com.google.api.services.calendar.CalendarRequest;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.common.collect.ImmutableSet;

public class CreateCalendarTask {


	private static final Calendar _service = getCalendarService();

	public static void main(String[] args) throws IOException, NoSuchProviderException,
			MessagingException, GeneralSecurityException {
		CalendarRequest<Event> calendarAction;
		if (args.length < 1) {
			throw new RuntimeException("No task text specified");
		}
		calendarAction = createInsertTask(args[0]);
		commit(calendarAction);

	}

	private static CalendarRequest<Event> createInsertTask(String title) throws IOException {
		CalendarRequest<Event> updateTask;
		Event event = new Event();
		event.setSummary(title);
		// 3 hours from now
		event.setStart(new EventDateTime().setDateTime(new DateTime(System.currentTimeMillis() + 10800000)));
		// 3.5 hours from now
		event.setEnd(new EventDateTime().setDateTime(new DateTime(System.currentTimeMillis() + 10800000 + 1800000)));
		updateTask = _service.events().insert("primary", event);
		return updateTask;
	}

	private static void commit(final CalendarRequest<Event> update) throws NoSuchProviderException,
			MessagingException, IOException {

		// All persistent changes are done right at the end, so that any
		// exceptions can get thrown first.

		executeCalendarRequest(update);
	}

	private static void executeCalendarRequest(final CalendarRequest<Event> update) {
		new Thread() {
			@Override
			public void run() {
				Event updatedEvent;
				try {
					updatedEvent = update.execute();
					// Print the updated date.
					System.out.println(updatedEvent.getUpdated());
					System.out.println(updatedEvent.getHtmlLink());
					System.out.println("Calendar updated");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}.start();
	}

	private static Calendar getCalendarService() {
		System.out.println("Authenticating...");

		HttpTransport httpTransport;
		try {
			httpTransport = GoogleNetHttpTransport.newTrustedTransport();

			Calendar client = new Calendar.Builder(httpTransport,
					JacksonFactory.getDefaultInstance(), new AuthorizationCodeInstalledApp(
							new GoogleAuthorizationCodeFlow.Builder(httpTransport,
									JacksonFactory.getDefaultInstance(), GoogleClientSecrets.load(
											JacksonFactory.getDefaultInstance(),
											new InputStreamReader(CreateCalendarTask.class
													.getResourceAsStream("/client_secrets.json"))),
									ImmutableSet.of(CalendarScopes.CALENDAR,
											CalendarScopes.CALENDAR_READONLY)).setDataStoreFactory(
									new FileDataStoreFactory(new java.io.File(System
											.getProperty("user.home"), ".store/calendar_sample")))
									.build(), new LocalServerReceiver()).authorize("user"))
					.setApplicationName("gcal-task-warrior").build();
			return client;
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;

	}

	private static Store connect() throws NoSuchProviderException, MessagingException {
		Properties props = System.getProperties();
		String password = System.getenv("GMAIL_PASSWORD");
		if (password == null) {
			throw new RuntimeException(
					"Please specify your password by running export GMAIL_PASSWORD=mypassword groovy mail.groovy");
		}
		props.setProperty("mail.store.protocol", "imap");
		Store theImapClient = Session.getInstance(props).getStore("imaps");
		theImapClient.connect("imap.gmail.com", "sarnobat.hotmail@gmail.com", password);
		return theImapClient;
	}

}

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.mail.FetchProfile;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

public class ListDisplaySynchronous {
	static final String string = "/Users/sarnobat/.gcal_task_warrior";
	static final File file = new File(string + "/tasks.json");

	public static void main(String[] args) throws NoSuchProviderException,
			MessagingException, IOException {

		writeCalendarsToFileInSeparateThread();
		getErrands();

		System.out.println("List updated");
	}

	private static void writeCalendarsToFileInSeparateThread() {
		new Thread() {
			public void run() {
				writeCalendars();
			}
		}.start();
	}

	private static void writeCalendars() {

		JSONObject json;
		try {
			json = getCalendars();

			final String string = "/Users/sarnobat/.gcal_task_warrior";
			final File file = new File(string + "/calendars.json");
			FileUtils.writeStringToFile(file, json.toString(2), "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static JSONObject getCalendars() throws GeneralSecurityException,
			IOException, UnsupportedEncodingException {
		Calendar client = getCalendarService();

		System.out.println("Getting calendars...");
		@SuppressWarnings("unchecked")
		List<CalendarListEntry> allCalendars = (List<CalendarListEntry>) client
				.calendarList().list().execute().get("items");
		JSONObject json = new JSONObject();

		for (CalendarListEntry aCalendar : allCalendars) {
			// System.out.println(aCalendar.getSummary() + "::"
			// + URLDecoder.decode(aCalendar.getId(), "UTF-8"));
			json.put(aCalendar.getSummary(),
					new JSONObject().put("calendar_id", aCalendar.getId()));
		}
		return json;
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
												ListDisplaySynchronous.class
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

	private static void getErrands() throws NoSuchProviderException,
			MessagingException, IOException {
		Message[] msgs = getMessages();
		System.out.println("Messages obtained");

		int postponeCount = getPostponeCount();
		JSONObject json = createJsonListOfEvents(msgs);
		json.put("daysToPostpone", postponeCount);
		
		FileUtils.writeStringToFile(file, json.toString(2));
	}

	private static int getPostponeCount() throws IOException {
		String errands = FileUtils.readFileToString(file);

		JSONObject allErrandsJsonOriginal = new JSONObject(errands);

		int daysToPostponeSaved;
		if (allErrandsJsonOriginal.has("daysToPostpone")) {
			daysToPostponeSaved = allErrandsJsonOriginal
					.getInt("daysToPostpone");
		} else {
			daysToPostponeSaved = 30;
		}
		return daysToPostponeSaved;
	}

	private static JSONObject createJsonListOfEvents(Message[] msgs)
			throws MessagingException {

		// JSONObject jsonToBeSaved = new JSONObject();
		Map<String, JSONObject> messages = new TreeMap<String, JSONObject>();
		// int i = 0;
		for (Message aMessage : msgs) {
			// i++;
			JSONObject messageMetadata = Preconditions
					.checkNotNull(getMessageMetadata(aMessage));
			String[] aTitle = messageMetadata.getString("title").split("@");

			String repeating = "";
			if (aTitle.length > 1 && aTitle[1].contains("Repeating")) {
				repeating = "[Repeating] ";
			}
			String aTitleMain = aTitle[0].replace("Reminder: ", "");
			String printedTitle = repeating + aTitleMain;

			messages.put(StringUtils.capitalize(printedTitle), messageMetadata);
			// jsonToBeSaved.put(Integer.toString(i), messageMetadata);
		}
		int i = 0;
		JSONObject jsonToBeSaved = new JSONObject();
		for (String aTitle : new TreeSet<String>(messages.keySet())) {
			++i;
			JSONObject messageMetadata = messages.get(aTitle);
			System.out.println(i + "\t" + aTitle);
			jsonToBeSaved.put(Integer.toString(i), messageMetadata);
		}
		return jsonToBeSaved;
	}

	private static JSONObject getMessageMetadata(Message aMessage) {
		JSONObject errandJsonObject;
		try {
			errandJsonObject = new JSONObject();
			String title;
			// Leave this as-s for writing. Only when displaying should you
			// abbreviate
			title = aMessage.getSubject();

			errandJsonObject.put("title", title);
			// getBodyMetadataSlow(aMessage);
			return errandJsonObject;
		} catch (MessagingException e) {
			e.printStackTrace();
		}
		return null;
	}

	private static Message[] getMessages() throws NoSuchProviderException,
			MessagingException {
		System.out.println("Connecting");
		Store theImapClient = connect();
		Folder folder = theImapClient
				.getFolder("3 - Urg - time sensitive - this week");
		System.out.println("Opening");
		folder.open(Folder.READ_ONLY);

		System.out.println("Getting Message list");
		Message[] msgs = folder.getMessages();

		FetchProfile fp = new FetchProfile();
		fp.add(FetchProfile.Item.ENVELOPE);
		System.out.print("Fetching attributes...");
		folder.fetch(msgs, fp);
		System.out.println("done");
		return msgs;
	}

	private static Store connect() throws NoSuchProviderException,
			MessagingException {
		Properties props = System.getProperties();
		String password = System.getenv("GMAIL_PASSWORD");
		if (password == null) {
			throw new RuntimeException(
					"Please specify your password by running export GMAIL_PASSWORD=mypassword groovy mail.groovy");
		}
		props.setProperty("mail.store.protocol", "imap");
		Store theImapClient = Session.getInstance(props).getStore("imaps");
		theImapClient.connect("imap.gmail.com", "sarnobat.hotmail@gmail.com",
				password);
		return theImapClient;
	}
}

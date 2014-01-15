package com.google.api.services.samples.calendar.cmdline;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Enumeration;
import java.util.Properties;

import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;

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
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.common.collect.ImmutableSet;

public class Postpone {
	@Deprecated
	private static final String MESSAGE_ID = "Message-ID";
	static final String dirPath = "/Users/sarnobat/.gcal_task_warrior";
	static final File tasksFileLastDisplayed = new File(dirPath
			+ "/tasks_last_displayed.json");
	static final File tasksFileLatest = new File(dirPath + "/tasks.json");

	public static void main(String[] args) throws IOException,
			NoSuchProviderException, MessagingException,
			GeneralSecurityException {
		//tooSlow(args);
	}

	@SuppressWarnings("unused")
	@Deprecated
	private static void tooSlow(String[] args) throws IOException,
			GeneralSecurityException, NoSuchProviderException,
			MessagingException {
		String itemToDelete = args[0];
		String daysToPostponeString = args[1];
		JSONObject eventJson = getEventJson(itemToDelete);
		Update update = createUpdateCall(daysToPostponeString, eventJson);

		String messageIdToDelete = eventJson.getString(MESSAGE_ID);

		System.out.println("Will delete [" + messageIdToDelete + "] "
				+ eventJson.getString("title") + " from calendar ");
		getMessages(messageIdToDelete);
		JSONObject fileJsonLastDisplayed = getReducedJson(itemToDelete,
				messageIdToDelete, tasksFileLastDisplayed);

		JSONObject fileJsonLatest = getReducedJson(itemToDelete,
				messageIdToDelete, tasksFileLatest);

		// All persistent changes are done right at the end, so that any
		// exceptions can get thrown first.
		commit(update, fileJsonLastDisplayed, fileJsonLatest);
	}

	private static Update createUpdateCall(String daysToPostponeString,
			JSONObject eventJson) throws IOException, GeneralSecurityException {
		String calendarName = eventJson.getString("calendar_name");
		System.out.println(calendarName);
		Update update = createUpdateTask(eventJson, calendarName,
				daysToPostponeString);
		return update;
	}

	private static JSONObject getEventJson(String itemToDelete)
			throws IOException {
		String errands = FileUtils.readFileToString(tasksFileLastDisplayed);
		JSONObject eventJson = getEventJson(itemToDelete, errands);
		return eventJson;
	}

	private static JSONObject getEventJson(String itemToDelete, String errands) {
		JSONObject obj = new JSONObject(errands);
		JSONObject eventJson = (JSONObject) obj.get(itemToDelete);
		return eventJson;
	}

	private static void commit(Update update, JSONObject fileJson1,
			JSONObject fileJson2) throws IOException {
		commit: {
			FileUtils.writeStringToFile(tasksFileLastDisplayed,
					fileJson1.toString());
			FileUtils.writeStringToFile(tasksFileLatest, fileJson2.toString());
			Event updatedEvent = update.execute();

			// Print the updated date.
			System.out.println(updatedEvent.getUpdated());
			System.out.println(updatedEvent.getHtmlLink());
			System.out.println("Event updated");
		}
	}

	private static void getMessages(String messageIdToDelete)
			throws NoSuchProviderException, MessagingException {
		Message[] messages;
		{
			messages = getMessages();
			for (Message aMessage : messages) {
				String aMessageID = getMessageID(aMessage);
				if (aMessageID.equals(messageIdToDelete)) {
					aMessage.setFlag(Flags.Flag.DELETED, true);
					System.out.println("Deleted " + aMessage.getSubject());
					break;
				}
			}
		}
	}

	private static Update createUpdateTask(JSONObject eventJson,
			String calendarName, String daysToPostponeString)
			throws IOException, GeneralSecurityException {
		Update update;
		_1: {
			String calendars = FileUtils.readFileToString(new File(dirPath
					+ "/calendars.json"));
			JSONObject calendarJson = getEventJson(calendarName, calendars);
			System.out.println(calendarJson.toString());
			String calendarId = calendarJson.getString("calendar_id");
			String eventID = eventJson.getString("eventID");

			System.out.println("Will update event " + eventID + " in calendar "
					+ calendarId);

			update = createUpdateTask(calendarName, calendarId, eventID,
					daysToPostponeString);
		}
		return update;
	}

	private static Update createUpdateTask(String calendarName,
			String calendarId, String eventID, String daysToPostponeString)
			throws GeneralSecurityException, IOException {
		Update update;
		createUpdateTask: {
			int daysToPostpone = Integer.parseInt(daysToPostponeString);
			// Get event's current time
			update = createUpdateTask(calendarName, calendarId, eventID,
					daysToPostpone);
		}
		return update;
	}

	private static Update createUpdateTask(String calendarName,
			String calendarId, String eventID, int daysToPostpone)
			throws GeneralSecurityException, IOException {
		Update update;
		_3: {
			Events events = getCalendarService().events();
			com.google.api.services.calendar.model.CalendarList lc = getCalendarService()
					.calendarList().list().execute();
			CalendarListEntry calendar = null;
			System.out.println(lc.getItems().size());
			for (CalendarListEntry c : lc.getItems()) {
				if (calendarName.equals(c.getSummary())) {
					calendar = c;
				}
			}
			if (calendar == null) {
				throw new RuntimeException("couldn't find calendar");
			}
			com.google.api.services.calendar.model.Events s = events.list(
					calendar.getId()).execute();
			Event target = null;
			findCalendarEvent: {
				com.google.api.services.calendar.model.Events events2;

				String pageToken = null;

				while (true) {
					events2 = getCalendarService().events().list("primary")
							.setPageToken(pageToken).execute();
					events2 = getCalendarService().events().list("primary")
							.setPageToken(pageToken).execute();
					java.util.List<Event> items = events2.getItems();
					for (Event e : items) {
						if (e.getHtmlLink() != null
								&& e.getHtmlLink().contains(eventID)) {
							target = e;
						}
					}
					pageToken = events2.getNextPageToken();
					if (pageToken == null) {
						break;
					}
				}
			}

			if (target == null) {
				throw new RuntimeException("Couldn't find event");
			}

			System.out.println(target);
			Event clonedEvent = target.clone();
			if (clonedEvent.getRecurrence() != null) {
				throw new RuntimeException(
						"Use optional param 'singleEvents' to break recurring events into single ones");
			}

			update = createUpdateTask(calendarId, daysToPostpone, target);
		}
		return update;
	}

	private static Update createUpdateTask(String calendarId,
			int daysToPostpone, Event target) throws IOException,
			GeneralSecurityException {
		Update update;
		createUpdateTask1: {
			// First retrieve the event from the API.
			Event event = getCalendarService().events()
					.get(calendarId, target.getId()).execute();

			java.util.Calendar c = java.util.Calendar.getInstance();
			c.add(java.util.Calendar.DATE, daysToPostpone);
			System.out.println(c.getTime());
			_4: {
				EventDateTime startTime = event.getStart();
				System.out.println(startTime);
				System.out.println(target.getId());

				long dateTime = c.getTimeInMillis();
				startTime.setDateTime(new DateTime(dateTime));

				EventDateTime endTime = event.getEnd();
				long endTimeMillis = c.getTimeInMillis();
				endTime.setDateTime(new DateTime(endTimeMillis));
			}
			update = getCalendarService().events().update(calendarId,
					target.getId(), event);
		}
		return update;
	}

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
												Postpone.class
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

	private static JSONObject getReducedJson(String itemToDelete,
			String messageIdToDelete, File file2) throws IOException {
		String latestFileContents = FileUtils.readFileToString(file2);
		JSONObject fileJson = new JSONObject(latestFileContents);
		JSONObject removed = (JSONObject) fileJson.remove(itemToDelete);
		if (!messageIdToDelete.equals(removed.getString(MESSAGE_ID))) {
			throw new RuntimeException(removed.getString("title"));
		}
		return fileJson;
	}

	private static Message[] getMessages() throws NoSuchProviderException,
			MessagingException {
		Store theImapClient = connect();
		Folder folder = theImapClient
				.getFolder("3 - Urg - time sensitive - this week");
		folder.open(Folder.READ_WRITE);

		Message[] msgs = folder.getMessages();

		FetchProfile fp = new FetchProfile();
		fp.add(FetchProfile.Item.ENVELOPE);
		fp.add("X-mailer");
		folder.fetch(msgs, fp);
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

	@SuppressWarnings("unchecked")
	private static String getMessageID(Message aMessage)
			throws MessagingException {
		Enumeration<Header> allHeaders = (Enumeration<Header>) aMessage
				.getAllHeaders();
		String messageID = "<not found>";
		while (allHeaders.hasMoreElements()) {
			Header e = (Header) allHeaders.nextElement();
			if (e.getName().equals(MESSAGE_ID)) {
				messageID = e.getValue();
			}
		}
		return messageID;
	}

}

package com.google.api.services.samples.calendar.cmdline;

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
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.common.collect.ImmutableSet;

public class Postpone {

	private static final String MESSAGE_ID = "Message-ID";

	private static final String DIR_PATH = "/Users/sarnobat/.gcal_task_warrior";
	private static final File mTasksFileLastDisplayed = new File(DIR_PATH
			+ "/tasks_last_displayed.json");
	private static final File mTasksFileLatest = new File(DIR_PATH
			+ "/tasks.json");
	private static final Calendar mCs = getCalendarService();

	public static void main(String[] args) throws IOException,
			NoSuchProviderException, MessagingException,
			GeneralSecurityException {
		String itemToDelete;
		String daysToPostponeString;

		if (args.length == 0) {
			itemToDelete = "1";
			daysToPostponeString = "1";
		} else {
			itemToDelete = args[0];
			daysToPostponeString = args[1];
		}
		// postpone(itemToDelete, daysToPostponeString);
		JSONObject eventJson = getEventJson(itemToDelete, mTasksFileLatest);
		String title = eventJson.getString("title");
		System.out.println(title);
		Message msg = getMessage(title);
		String messageIdToDelete = getMessageID(msg);
		String eventId = getEventID(msg);
		String calendarName = getCalendarName(msg);
		String calendarId = getCalendarIdViaCalendarService();
		Update updateTask = createUpdateTask(calendarName, calendarId, eventId,
				daysToPostponeString);
		commit(itemToDelete, updateTask, messageIdToDelete);
	}

	private static Message getMessage(String title)
			throws NoSuchProviderException, MessagingException {
		Message[] msgs = getMessages();
		Message msg = null;
		for (Message aMsg : msgs) {
			if (aMsg.getSubject().equals(title)) {
				msg = aMsg;
				break;
			}
		}
		if (msg == null) {
			throw new RuntimeException();
		}
		return msg;
	}

	private static void commit(String itemToDelete, Update update,
			String messageIdToDelete) throws NoSuchProviderException,
			MessagingException, IOException {
		getMessages(messageIdToDelete);
		JSONObject fileJsonLastDisplayed = getReducedJson(itemToDelete,
				messageIdToDelete, mTasksFileLastDisplayed);

		JSONObject fileJsonLatest = getReducedJson(itemToDelete,
				messageIdToDelete, mTasksFileLatest);

		// All persistent changes are done right at the end, so that any
		// exceptions can get thrown first.
		commitInternal(update, fileJsonLastDisplayed, fileJsonLatest);
	}

	// Still useful
	private static JSONObject getEventJson(String itemToDelete,
			File tasksFileLastDisplayed) throws IOException {
		String errands = FileUtils.readFileToString(tasksFileLastDisplayed);
		JSONObject eventJson = getEventJson(itemToDelete, errands);
		return eventJson;
	}

	private static JSONObject getEventJson(String itemToDelete, String errands) {
		JSONObject allErrandsJson = new JSONObject(errands);
		JSONObject eventJson = (JSONObject) allErrandsJson.get(itemToDelete);
		return eventJson;
	}

	private static void commitInternal(Update update, JSONObject fileJson1,
			JSONObject fileJson2) throws IOException {
		commit: {
			FileUtils.writeStringToFile(mTasksFileLastDisplayed,
					fileJson1.toString());
			FileUtils.writeStringToFile(mTasksFileLatest, fileJson2.toString());
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

	// Useful
	private static Update createUpdateTask(String calendarName,
			String calendarId, String eventID, String daysToPostponeString)
			throws GeneralSecurityException, IOException {
		int daysToPostpone = Integer.parseInt(daysToPostponeString);
		// Get event's current time

		thisDoesNothing(calendarName);
		return createUpdateTask(calendarId, eventID, daysToPostpone);
	}

	// TODO: delete this?
	@Deprecated
	private static void thisDoesNothing(String calendarName)
			throws GeneralSecurityException, IOException {

		{
			Events events = mCs.events();
			CalendarListEntry calendar = getCalendar(calendarName);

			com.google.api.services.calendar.model.Events s = events.list(
					calendar.getId()).execute();
		}
	}

	private static CalendarListEntry getCalendar(String calendarName)
			throws IOException, GeneralSecurityException {
		com.google.api.services.calendar.model.CalendarList theCalendarList = mCs
				.calendarList().list().execute();
		CalendarListEntry calendar = null;
		System.out.println(theCalendarList.getItems().size());
		for (CalendarListEntry aCalendar : theCalendarList.getItems()) {
			if (calendarName.equals(aCalendar.getSummary())) {
				calendar = aCalendar;
			}
		}
		if (calendar == null) {
			throw new RuntimeException("couldn't find calendar");
		}
		return calendar;
	}

	private static String getEventID(Message aMessage) throws IOException,
			MessagingException {
		String eventID = "<none>";
		{
			MimeMultipart s = (MimeMultipart) aMessage.getContent();
			{
				String body = (String) s.getBodyPart(0).getContent();

				if (body.trim().length() < 1) {
					System.out.println("body is empty");
				}

				if (body.contains("eid")) {
					Pattern pattern = Pattern.compile("eid=([^&" + '$'
							+ "\\s]*)");
					Matcher m = pattern.matcher(body);
					if (!m.find()) {
						throw new RuntimeException("eid not in string 2");
					}
					eventID = m.group(1);
				}
			}
		}
		return eventID;
	}

	private static Event getEvent(String eventID) throws IOException,
			GeneralSecurityException {
		Event target = null;
		findCalendarEvent: {
			com.google.api.services.calendar.model.Events events2;

			String pageToken = null;

			while (true) {
				events2 = mCs.events().list("primary").setPageToken(pageToken)
						.execute();
				events2 = mCs.events().list("primary").setPageToken(pageToken)
						.execute();
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

		return target;
	}

	private static Update createUpdateTask(String calendarId, String eventID,
			int daysToPostpone) throws IOException, GeneralSecurityException {
		Event originalEvent = getEvent(eventID);

		if (originalEvent.getRecurrence() != null) {
			throw new RuntimeException(
					"Use optional param 'singleEvents' to break recurring events into single ones");
		}

		// I don't know why the service uses a different ID
		String internalEventId = originalEvent.getId();
		Event event = mCs.events().get(calendarId, internalEventId).execute();
		{
			EventDateTime eventStartTime = event.getStart();
			System.out.println(eventStartTime);
			long newStartTime = getNewStartTime(daysToPostpone);
			eventStartTime.setDateTime(new DateTime(newStartTime));

			EventDateTime endTime = event.getEnd();
			long endTimeMillis = getNewEndDateTime(daysToPostpone);
			endTime.setDateTime(new DateTime(endTimeMillis));

		}
		System.out.println(internalEventId);
		Update update = mCs.events().update(calendarId, internalEventId, event);

		return update;
	}

	private static long getNewEndDateTime(int daysToPostpone) {
		long endTimeMillis;
		{
			java.util.Calendar c = java.util.Calendar.getInstance();
			c.add(java.util.Calendar.DATE, daysToPostpone);

			endTimeMillis = c.getTimeInMillis();
		}
		return endTimeMillis;
	}

	private static long getNewStartTime(int daysToPostpone) {
		{
			java.util.Calendar c = java.util.Calendar.getInstance();
			c.add(java.util.Calendar.DATE, daysToPostpone);
			long newStartDateTimeMillis = c.getTimeInMillis();
			System.out.println(c.getTime());
			return newStartDateTimeMillis;
		}
	}

	private static Calendar getCalendarService() {
		System.out.println("Authenticating...");

		HttpTransport httpTransport;
		try {
			httpTransport = GoogleNetHttpTransport.newTrustedTransport();

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
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;

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

	@Deprecated
	// It's too expensive to get the event ID before deleting
	private static class PostponeByMessageID {

		@Deprecated
		private static Update createUpdateCall(String daysToPostponeString,
				JSONObject eventJson) throws IOException,
				GeneralSecurityException {
			String calendarName = eventJson.getString("calendar_name");
			System.out.println(calendarName);
			Update update = createUpdateTaskDeprecated(eventJson, calendarName,
					daysToPostponeString);
			return update;
		}

		@Deprecated
		private static Update createUpdateTaskDeprecated(JSONObject eventJson,
				String calendarName, String daysToPostponeString)
				throws IOException, GeneralSecurityException {
			Update update;
			_1: {
				String calendars = FileUtils.readFileToString(new File(DIR_PATH
						+ "/calendars.json"));
				JSONObject calendarJson = getEventJson(calendarName, calendars);
				System.out.println(calendarJson.toString());
				String calendarId = calendarJson.getString("calendar_id");
				String eventID = eventJson.getString("eventID");

				System.out.println("Will update event " + eventID
						+ " in calendar " + calendarId);

				update = createUpdateTask(calendarName, calendarId, eventID,
						daysToPostponeString);
			}
			return update;
		}

		@SuppressWarnings("unused")
		@Deprecated
		private static void postpone(String itemToDelete,
				String daysToPostponeString) throws IOException,
				GeneralSecurityException, NoSuchProviderException,
				MessagingException {
			JSONObject eventJson = getEventJson(itemToDelete,
					mTasksFileLastDisplayed);
			Update update = createUpdateCall(daysToPostponeString, eventJson);

			String messageIdToDelete = eventJson.getString(MESSAGE_ID);

			System.out.println("Will delete [" + messageIdToDelete + "] "
					+ eventJson.getString("title") + " from calendar ");
			commit(itemToDelete, update, messageIdToDelete);
		}
	}

	private static class Slow {
		private static void getBodyMetadataSlow(Message aMessage) {

			try {
				String eventID = getEventID(aMessage);

				// errandJsonObject.put("eventID", eventID);

				String calendarName = getCalendarName(aMessage);
				// errandJsonObject.put("calendar_name", calendarName);

				String messageID = getMessageID(aMessage);
				// errandJsonObject.put("Message-ID", messageID);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (MessagingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		private static String getCalendarName(Message aMessage)
				throws IOException, MessagingException {
			String calendarName;
			MimeMultipart s = (MimeMultipart) aMessage.getContent();
			String body1 = (String) s.getBodyPart(0).getContent();
			if (body1.contains("Calendar:")) {
				Pattern pattern = Pattern.compile("Calendar: (.*)");
				Matcher m = pattern.matcher(body1);
				if (!m.find()) {
					throw new RuntimeException("eid not in string 2");
				}
				calendarName = m.group(1);
			} else {
				calendarName = "<not found>";
			}

			return calendarName;
		}

		private static String getEventID(Message aMessage) throws IOException,
				MessagingException {
			String eventID = "<none>";
			MimeMultipart s = (MimeMultipart) aMessage.getContent();
			String body = (String) s.getBodyPart(0).getContent();

			if (body.trim().length() < 1) {
				System.out.println("body is empty");
			}

			if (body.contains("eid")) {
				Pattern pattern = Pattern.compile("eid=([^&" + '$' + "\\s]*)");
				Matcher m = pattern.matcher(body);
				if (!m.find()) {
					throw new RuntimeException("eid not in string 2");
				}
				eventID = m.group(1);
			}
			return eventID;
		}

		private static String getMessageID(Message aMessage) {
			try {
				Enumeration allHeaders;
				allHeaders = aMessage.getAllHeaders();

				String messageID = "<not found>";
				while (allHeaders.hasMoreElements()) {
					Header e = (Header) allHeaders.nextElement();
					if (e.getName().equals("Message-ID")) {
						messageID = e.getValue();
					}
				}
				return messageID;
			} catch (MessagingException e1) {
				e1.printStackTrace();
			}
			return null;
		}
	}

	private static String getCalendarName(Message aMessage) throws IOException,
			MessagingException {
		String calendarName;
		{
			MimeMultipart s = (MimeMultipart) aMessage.getContent();
			String body1 = (String) s.getBodyPart(0).getContent();
			if (body1.contains("Calendar:")) {
				Pattern pattern = Pattern.compile("Calendar: (.*)");
				Matcher m = pattern.matcher(body1);
				if (!m.find()) {
					throw new RuntimeException("eid not in string 2");
				}
				calendarName = m.group(1);
			} else {
				calendarName = "<not found>";
			}
		}
		return calendarName;
	}

}

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

public class Postpone {

	private static final String MESSAGE_ID = "Message-ID";

	private static final String DIR_PATH = "/Users/sarnobat/.gcal_task_warrior";
	private static final File mTasksFileLatest = new File(DIR_PATH
			+ "/tasks.json");
	private static final Calendar _service = getCalendarService();

	public static void main(String[] args) throws IOException,
			NoSuchProviderException, MessagingException,
			GeneralSecurityException {
		String itemToDelete;
		String daysToPostponeString;

		if (args.length == 0) {
			itemToDelete = "1";
			daysToPostponeString = "1";
		} else if (args.length == 1) {
			itemToDelete = args[0];
			
			// TODO: move this to the commit section. Don't write the updated json out until the service calls succeed
			_1:{	
				String errands = FileUtils.readFileToString(mTasksFileLatest);
				JSONObject allErrandsJsonModified;

				JSONObject allErrandsJsonOriginal = new JSONObject(errands);
				allErrandsJsonModified = allErrandsJsonOriginal ;
				

				int daysToPostponeInitial = 30;
				if (allErrandsJsonOriginal.has("daysToPostpone")) {
					int daysToPostponeSaved = allErrandsJsonOriginal
							.getInt("daysToPostpone");
					
					int daysToPostponeIncremented = daysToPostponeSaved + 1;
					if (daysToPostponeIncremented >= daysToPostponeInitial * 3) {
						daysToPostponeIncremented = daysToPostponeInitial;
					}
					daysToPostponeString = Integer
							.toString(daysToPostponeIncremented);
					allErrandsJsonModified.put("daysToPostpone",
							daysToPostponeIncremented);
				} else {

					daysToPostponeString = Integer.toString(daysToPostponeInitial);
					allErrandsJsonModified.put("daysToPostpone",
							daysToPostponeInitial);
				}
				FileUtils.writeStringToFile(mTasksFileLatest,
						allErrandsJsonModified.toString(2));
			}
		} else {
			itemToDelete = args[0];
			daysToPostponeString = args[1];
		}
		System.out.println("Will postpone by " + daysToPostponeString + " days.");
		JSONObject eventJson = getEventJson(itemToDelete, mTasksFileLatest);
		String title = eventJson.getString("title");
		System.out.println("Title:\n\t" + title);
		Message msg = getMessage(title);
		String messageIdToDelete = getMessageID(msg);
		String eventId = getEventID(msg);
		System.out.println("Event ID\n\t" + eventId);
		String calendarName = getCalendarName(msg);
		System.out.println("Calendar name\n\t" + calendarName);
		String calendarId = getCalendarId(calendarName);
		System.out.println("Calendar ID:\n\t" + calendarId);
		CalendarRequest<Event> calendarAction;
		try {
			calendarAction = createUpdateTask(calendarName, calendarId, eventId,
					daysToPostponeString);
		} catch (IsRecurringEventException e) {

			calendarAction = createInsertTask(daysToPostponeString, title);
		}
		commit(calendarAction, messageIdToDelete);

	}

	private static CalendarRequest<Event> createInsertTask(
			String daysToPostponeString, String title) throws IOException {
		CalendarRequest<Event> updateTask;
		Event event = new Event();
		event.setSummary(title);
		event.setStart(new EventDateTime());
		event.setEnd(new EventDateTime());
		int daysToPostpone = Integer.parseInt(daysToPostponeString);
		postponeEvent(daysToPostpone, event);
		updateTask = _service.events().insert("primary", event);
		return updateTask;
	}

	private static String getCalendarId(String calendarName) {

		final String string = "/Users/sarnobat/.gcal_task_warrior";
		final File file = new File(string + "/calendars.json");
		String s;
		try {
			s = FileUtils.readFileToString(file, "UTF-8");
			JSONObject j = new JSONObject(s);
			JSONObject jsonObject = (JSONObject) j.get(calendarName);
			String id = jsonObject.getString("calendar_id");
			return id;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
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

	private static void commit(final CalendarRequest<Event> update,
			final String messageIdToDelete) throws NoSuchProviderException,
			MessagingException, IOException {

		// All persistent changes are done right at the end, so that any
		// exceptions can get thrown first.
		deleteEmailInSeparateThread(messageIdToDelete);

		executeCalendarRequest(update);
	}

	private static void executeCalendarRequest(
			final CalendarRequest<Event> update) {
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

	private static void deleteEmailInSeparateThread(
			final String messageIdToDelete) {
		new Thread() {
			@Override
			public void run() {
				try {
					deleteEmail(messageIdToDelete);
				} catch (NoSuchProviderException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (MessagingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}.start();
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

	private static void deleteEmail(String messageIdToDelete)
			throws NoSuchProviderException, MessagingException {
		Message[] messages;
		messages = getMessages();
		for (Message aMessage : messages) {
			String aMessageID = getMessageID(aMessage);
			if (aMessageID.equals(messageIdToDelete)) {
				aMessage.setFlag(Flags.Flag.DELETED, true);
				System.out.println("Deleted email:\t" + aMessage.getSubject());
				break;
			}
		}

	}

	// Useful
	private static Update createUpdateTask(String calendarName,
			String calendarId, String eventID, String daysToPostponeString)
			throws GeneralSecurityException, IOException,
			IsRecurringEventException {
		int daysToPostpone = Integer.parseInt(daysToPostponeString);
		// Get event's current time

		thisDoesNothing(calendarName);
		return createUpdateTask(calendarId, eventID, daysToPostpone);
	}

	// TODO: delete this?
	@Deprecated
	private static void thisDoesNothing(String calendarName)
			throws GeneralSecurityException, IOException {

		Events events = _service.events();
		CalendarListEntry calendar = getCalendar(calendarName);

		com.google.api.services.calendar.model.Events s = events.list(
				calendar.getId()).execute();
	}

	private static CalendarListEntry getCalendar(String calendarName)
			throws IOException, GeneralSecurityException {
		com.google.api.services.calendar.model.CalendarList theCalendarList = _service
				.calendarList().list().execute();
		CalendarListEntry calendar = null;
		System.out.println("Number of calendars (not needed):\n\t"
				+ theCalendarList.getItems().size());
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

	private static Update createUpdateTask(String calendarId, String eventID,
			int daysToPostpone) throws IOException, GeneralSecurityException,
			IsRecurringEventException {
		Event originalEvent = getEvent(eventID, calendarId);

		if (originalEvent.getRecurrence() != null) {
			throw new RuntimeException(
					"Use optional param 'singleEvents' to break recurring events into single ones");
		}

		// I don't know why the service uses a different ID
		String internalEventId = originalEvent.getId();
		Event event = _service.events().get(calendarId, internalEventId)
				.execute();
		postponeEvent(daysToPostpone, event);
		System.out.println("Internal Event ID:\t" + internalEventId);
		Update update = _service.events().update(calendarId, internalEventId,
				event);

		return update;
	}

	private static void postponeEvent(int daysToPostpone, Event event) {
		_1: {
			EventDateTime eventStartTime = event.getStart();
			System.out.println("Event original start time:\t" + eventStartTime);
			long newStartTime = getNewStartTime(daysToPostpone);
			eventStartTime.setDateTime(new DateTime(newStartTime));

			EventDateTime endTime = event.getEnd();
			long endTimeMillis = getNewEndDateTime(daysToPostpone);
			endTime.setDateTime(new DateTime(endTimeMillis));

		}
	}

	private static Event getEvent(String iEventId, String iCalendarId)
			throws IOException, GeneralSecurityException,
			IsRecurringEventException {
		Event theTargetEvent = getNonRecurringEvent(iEventId);

		if (theTargetEvent == null) {

			com.google.api.services.calendar.model.Events allEventsList;

			String aNextPageToken = null;

			while (true) {
				allEventsList = _service.events().list(iCalendarId)
						.setPageToken(aNextPageToken).execute();
				java.util.List<Event> allEventItems = allEventsList.getItems();
				for (Event anEvent : allEventItems) {
					String anHtmlLink = anEvent.getHtmlLink();
					// System.out.println(anHtmlLink);
					// System.out.println("\t"+anEvent.getSummary());
					if (anHtmlLink != null && anHtmlLink.contains(iEventId)) {
						theTargetEvent = anEvent;
					}
				}
				aNextPageToken = allEventsList.getNextPageToken();
				if (aNextPageToken == null) {
					break;
				}
			}

			if (theTargetEvent == null) {
				throw new IsRecurringEventException(
						"Couldn't find event in service: https://www.google.com/calendar/render?eid="
								+ iEventId
								+ " . Perhaps it is a repeated event? The event ID in the email is the latest one; the html link from the service is the first in the series. I can't get instances() to return all instances in the series because I think you need to pass the first event Id, not the latest. ");
			}
		}

		//System.out.println("Event:\n\t" + theTargetEvent);

		return theTargetEvent;
	}

	private static Event getNonRecurringEvent(String iEventId)
			throws IOException {
		Event theTargetEvent = null;
		findCalendarEvent: {
			com.google.api.services.calendar.model.Events allEventsList;

			String aNextPageToken = null;

			while (true) {
				allEventsList = _service.events().list("primary")
						.setPageToken(aNextPageToken).execute();
				java.util.List<Event> allEventItems = allEventsList.getItems();
				for (Event anEvent : allEventItems) {
					// System.out.println(anEvent.getSummary());
					String anHtmlLink = anEvent.getHtmlLink();
					if (anHtmlLink != null && anHtmlLink.contains(iEventId)) {
						theTargetEvent = anEvent;
					}
				}
				aNextPageToken = allEventsList.getNextPageToken();
				if (aNextPageToken == null) {
					break;
				}
			}
		}
		return theTargetEvent;
	}

	private static long getNewEndDateTime(int daysToPostpone) {
		long endTimeMillis;
		java.util.Calendar c = java.util.Calendar.getInstance();
		c.add(java.util.Calendar.DATE, daysToPostpone);

		endTimeMillis = c.getTimeInMillis();

		return endTimeMillis;
	}

	private static long getNewStartTime(int daysToPostpone) {
		java.util.Calendar c = java.util.Calendar.getInstance();
		c.add(java.util.Calendar.DATE, daysToPostpone);
		long newStartDateTimeMillis = c.getTimeInMillis();
		System.out.println("New start time:\t" + c.getTime());
		return newStartDateTimeMillis;
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

	private static Message[] getMessages() throws NoSuchProviderException,
			MessagingException {
		Store theImapClient = connect();
		Folder folder = theImapClient
				.getFolder("3 - Urg - time sensitive");
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

	private static String getCalendarName(Message aMessage) throws IOException,
			MessagingException {
		String calendarName;
		_2: {
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
				throw new RuntimeException(aMessage.getSubject());
			}
		}
		return calendarName;
	}

	private static class IsRecurringEventException extends Exception {
		IsRecurringEventException(String message) {
			super(message);
		}
	}
}

package com.google.api.services.samples.calendar.cmdline;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
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
import com.google.api.services.calendar.Calendar.CalendarList;
import com.google.api.services.calendar.Calendar.Events;
import com.google.api.services.calendar.Calendar.Events.List;
import com.google.api.services.calendar.Calendar.Events.Patch;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.common.collect.ImmutableSet;

public class Postpone {
	static final String string = "/Users/sarnobat/.gcal_task_warrior";
	static final String string2 = string + "/tasks_last_displayed.json";
	static final File file = new File(string2);

	public static void main(String[] args) throws IOException,
			NoSuchProviderException, MessagingException,
			GeneralSecurityException {
		String itemToDelete = "11";// args[0];
		String errands = FileUtils.readFileToString(file);
		JSONObject obj = new JSONObject(errands);
		JSONObject eventJson = (JSONObject) obj.get(itemToDelete);
		String calendarName = eventJson.getString("calendar_name");
		System.out.println(calendarName);

		_1: {
			String calendars = FileUtils.readFileToString(new File(string
					+ "/calendars.json"));
			JSONObject calendarsJson = new JSONObject(calendars);
			JSONObject calendarJson = (JSONObject) calendarsJson
					.get(calendarName);
			System.out.println(calendarJson.toString());
			String calendarId = calendarJson.getString("calendar_id");
			String eventID = eventJson.getString("eventID");

			System.out.println("Will update event " + eventID + " in calendar "
					+ calendarId);

			_2: {
				int daysToPostpone = 1;
				// Get event's current time
				_3: {
					Events events = getCalendarService().events();
					com.google.api.services.calendar.model.CalendarList lc = getCalendarService()
							.calendarList().list().execute();
					CalendarListEntry calendar = null;
					System.out.println(lc.getItems().size());
					for (CalendarListEntry c : lc.getItems()) {
						if (calendarName.equals(c.getSummary())) {
							// System.out.println(c.getSummary());
							calendar = c;
						}
					}
					if (calendar == null) {
						throw new RuntimeException("couldn't find calendar");
					}
					com.google.api.services.calendar.model.Events s = events
							.list(calendar.getId()).execute();
					// System.out.println(s.values());
					// java.util.List<Event> l = s.getItems();
					// Map m = new HashMap();
					Event target = null;
					{
						com.google.api.services.calendar.model.Events events2;

						String pageToken = null;
						do {
							events2 = getCalendarService().events()
									.list("primary").setPageToken(pageToken)
									.execute();
							java.util.List<Event> items = events2.getItems();
							for (Event e : items) {
								// System.out.println(e.getSummary());
								if (e.getHtmlLink() != null
										&& e.getHtmlLink().contains(eventID)) {
									target = e;
								}
							}
							pageToken = events2.getNextPageToken();
						} while (pageToken != null);

					}
					{
						// for (Event e : l) {
						// if (e.getHtmlLink().contains(eventID)) {
						// target = e;
						// }
						// //
						// System.out.println(e.getStart()+"::"+e.getSummary());
						// }
					}
					if (target == null) {
						throw new RuntimeException("Couldn't find event");
					}
					// Event l1 = l.get(0);
					// System.out.println(l1);

					System.out.println(target);
					Event clonedEvent = target.clone();
					// events.get(calendarId, eventID).execute().clone();
					if (clonedEvent.getRecurrence() != null) {
						throw new RuntimeException(
								"Use optional param 'singleEvents' to break recurring events into single ones");
					}

					{
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
							// Patch patch = events.patch(calendarId,
							// target.getId(),
							// clonedEvent);
							// patch.execute();
						}
						Event updatedEvent = getCalendarService().events()
								.update(calendarId, target.getId(), event)
								.execute();

						// Print the updated date.
						System.out.println(updatedEvent.getUpdated());
						System.out.println(event.getHtmlLink());
					}

				}

				// Set event's time as x days from now
			}
		}
		// _5: {
		// String messageIdToDelete = eventJson.getString("Message-ID");
		//
		// System.out.println("Will delete [" + messageIdToDelete + "] "
		// + eventJson.getString("title") + " from calendar "
		// + calendarName);
		// Message[] messages = getMessages();
		// for (Message aMessage : messages) {
		// String aMessageID = getMessageID(aMessage);
		// if (aMessageID.equals(messageIdToDelete)) {
		// aMessage.setFlag(Flags.Flag.DELETED, true);
		// System.out.println("Deleted " + aMessage.getSubject());
		// break;
		// }
		// }
		// deleteMessageFromLocalJson(itemToDelete, messageIdToDelete);
		// }

		System.out.println("Event updated");
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

	private static void deleteMessageFromLocalJson(String itemToDelete,
			String messageIdToDelete) throws IOException {
		String displayedFileContents = FileUtils.readFileToString(file);
		JSONObject displayedFileJson = new JSONObject(displayedFileContents);
		JSONObject removed = (JSONObject) displayedFileJson
				.remove(itemToDelete);
		if (!messageIdToDelete.equals(removed.getString("Message-ID"))) {
			throw new RuntimeException(removed.getString("title"));
		}
		FileUtils.writeStringToFile(file, displayedFileJson.toString());
		File file2 = new File(string + "/tasks.json");
		String latestFileContents = FileUtils.readFileToString(file2);
		JSONObject latestFileJson = new JSONObject(latestFileContents);
		JSONObject removed2 = (JSONObject) latestFileJson.remove(itemToDelete);
		if (!messageIdToDelete.equals(removed2.getString("Message-ID"))) {
			throw new RuntimeException(removed2.getString("title"));
		}
		FileUtils.writeStringToFile(file2, latestFileJson.toString());

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
		String password = "varelA77";// System.getenv("GMAIL_PASSWORD");
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

	private static String getMessageID(Message aMessage)
			throws MessagingException {
		Enumeration allHeaders = aMessage.getAllHeaders();
		String messageID = "<not found>";
		while (allHeaders.hasMoreElements()) {
			Header e = (Header) allHeaders.nextElement();
			if (e.getName().equals("Message-ID")) {
				messageID = e.getValue();
			}
		}
		return messageID;
	}

}

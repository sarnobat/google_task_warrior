import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.json.Json;
import javax.json.JsonObjectBuilder;
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
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.sun.net.httpserver.HttpServer;

public class NotNow {
	@Path("not_now")
	public static class HelloWorldResource { // Must be public
		private static final String string = "/home/sarnobat/.gcal_task_warrior";
		private static final File file = new File(string + "/tasks.json");
		
		@GET
		@Path("items")
		@Produces("application/json")
		public Response listItems(@QueryParam("rootId") Integer iRootId) throws Exception {
			try {
				JSONObject json2 = ListDisplaySynchronous.getErrandsJson();
				JSONObject json = new JSONObject();
				json.put("tasks", json2);
				FileUtils.writeStringToFile(file, json.toString(2));
				return Response.ok().header("Access-Control-Allow-Origin", "*")
						.entity(json.toString()).type("application/json")
						.build();
			} catch (Exception e) {
				e.printStackTrace();
				throw e;
			}
		}
		
		@GET
		@Path("delete")
		@Produces("application/json")
		public Response delete(@QueryParam("itemNumber") Integer iItemNumber) throws Exception {
			
			try {
				JSONObject json = new JSONObject();
				Delete.delete(iItemNumber.toString());
				return Response.ok().header("Access-Control-Allow-Origin", "*")
						.entity(json.toString()).type("application/json")
						.build();
			} catch (Exception e) {
				e.printStackTrace();
				throw e;
			}
		}

		
		@GET
		@Path("postpone")
		@Produces("application/json")
		public Response postpone(@QueryParam("itemNumber") Integer iItemNumber, @QueryParam("daysToPostpone") Integer iDaysToPostpone) throws IOException, NoSuchProviderException, MessagingException, GeneralSecurityException {
			System.out.println("1");
			try { 
				Postpone.postpone(iItemNumber.toString(), iDaysToPostpone.toString());
			} catch (Exception e) {
				System.out.println("!");
				e.printStackTrace();
				System.out.println(e);
			}
//			System.out.println("2");
//			System.out.println("3");
			JSONObject json = new JSONObject();
			return Response.ok().header("Access-Control-Allow-Origin", "*")
					.entity(json.toString()).type("application/json")
					.build();
		}
	}
	
	private static class Delete {

		private static final String DIR_PATH = "/home/sarnobat/.gcal_task_warrior";

		private static final File mTasksFileLatest = new File(DIR_PATH
				+ "/tasks.json");

		public static void delete(String itemToDelete) throws IOException,
				NoSuchProviderException, MessagingException {
			JSONObject eventJson = getEventJson(itemToDelete, mTasksFileLatest);
			String title = eventJson.getString("title");
			System.out.println("Title:\t" + title);
			Store theImapClient = connect();
			Set<Message> msgs = getMessages(title, theImapClient);
			for (Message msg : msgs) {
				if (msg == null) {
					throw new RuntimeException("msg is null");
				}
				String messageIdToDelete = getMessageID(msg);
				commit(itemToDelete, messageIdToDelete, theImapClient);
			}
			theImapClient.close();
		}

		private static JSONObject getEventJson(String itemToDelete,
				String errands) {
			JSONObject allErrandsJson = new JSONObject(errands);
			System.out.println(allErrandsJson);
			JSONObject eventJson = (JSONObject) allErrandsJson.getJSONObject(
					"tasks").get(itemToDelete);
			return eventJson;
		}

		// Still useful
		private static JSONObject getEventJson(String itemToDelete,
				File tasksFileLastDisplayed) throws IOException {
			String errands = FileUtils.readFileToString(tasksFileLastDisplayed);
			JSONObject eventJson = getEventJson(itemToDelete, errands);
			return eventJson;
		}

		private static Message getMessage(String title, Store theImapClient)
				throws NoSuchProviderException, MessagingException {
			Message[] msgs = getMessages(theImapClient);
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
		private static Set<Message> getMessages(String title, Store theImapClient)
				throws NoSuchProviderException, MessagingException {
			Message[] msgs = getMessages(theImapClient);
			ArrayList<Message> theMsgList = new ArrayList<Message>();
			System.out.println("Delete.getMessages() - looking for " + title);
			for (Message aMsg : msgs) {
				if (aMsg.getSubject().equals(title)) {
					theMsgList.add(checkNotNull(aMsg));
					System.out.println("Delete.getMessages() - matched: " + aMsg.getSubject());
				} else {
					System.out.println("Delete.getMessages() - No match: " + aMsg.getSubject());
				}
			}
			if (theMsgList.size() == 0) {
				throw new RuntimeException();
			}
			return ImmutableSet.copyOf(theMsgList);
		}

//		@Deprecated
//		private static Message[] getMessages() throws NoSuchProviderException,
//				MessagingException {
//			Store theImapClient = connect();
//			return getMessages(theImapClient);
//		}

		private static Message[] getMessages(Store theImapClient)
				throws MessagingException {
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

		private static String getMessageID(Message aMessage)
				throws MessagingException {
			if (aMessage == null) {
				System.out.println("aMessage is null");
			}
			Enumeration<?> allHeaders = aMessage.getAllHeaders();
			String messageID = "<not found>";
			while (allHeaders.hasMoreElements()) {
				Header e = (Header) allHeaders.nextElement();
				if (e.getName().equals("Message-ID")) {
					messageID = e.getValue();
				}
			}
			return messageID;
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
			theImapClient.connect("imap.gmail.com",
					"sarnobat.hotmail@gmail.com", password);
			return theImapClient;
		}

		private static void commit(String itemToDelete,
				final String messageIdToDelete, final Store theImapClient) throws NoSuchProviderException,
				MessagingException, IOException {

			// All persistent changes are done right at the end, so that any
			// exceptions can get thrown first.
//			new Thread() {
//				@Override
//				public void run() {
					try {
						deleteEmail(messageIdToDelete, theImapClient);
					} catch (NoSuchProviderException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (MessagingException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
//				}
//			}.start();

		}

		private static void deleteEmail(String messageIdToDelete, Store theImapClient)
				throws NoSuchProviderException, MessagingException {
			Message[] messages;
			messages = getMessages(theImapClient);
			for (Message aMessage : messages) {
				String aMessageID = getMessageID(aMessage);
				if (aMessageID.equals(messageIdToDelete)) {
					aMessage.setFlag(Flags.Flag.DELETED, true);
					System.out.println("Deleted email:\t"
							+ aMessage.getSubject());
					break;
				}
			}

		}
	}

	private static class ListDisplaySynchronous {
		static final String string = "/home/sarnobat/.gcal_task_warrior";
		static final File file = new File(string + "/tasks.json");


		static void writeCalendarsToFileInSeparateThread() {
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

				final String string = "/home/sarnobat/.gcal_task_warrior";
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
											// Only works if you launch the app from the same dir as the json file for some stupid reason
											new FileReader("/home/sarnobat/Desktop/new/github/not_now/client_secrets.json")),
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

		@Deprecated static void getErrands() throws NoSuchProviderException,
				MessagingException, IOException {
			JSONObject json2 = getErrandsJson();
			JSONObject json = new JSONObject();
			json.put("tasks",json2);
			
			FileUtils.writeStringToFile(file, json.toString(2));
		}

		static JSONObject getErrandsJson()
				throws NoSuchProviderException, MessagingException, IOException {
			Message[] msgs = getMessages();
			System.out.println("Messages obtained");

			int postponeCount = getPostponeCount();
			JSONObject json = createJsonListOfEvents(msgs);
			json.put("daysToPostpone", postponeCount);
			return json;
		}

		private static int getPostponeCount() throws IOException {
			int DAYS_TO_POSTPONE = 30;
			int daysToPostponeSaved = DAYS_TO_POSTPONE;
			if (!file.exists()) {
				daysToPostponeSaved = DAYS_TO_POSTPONE;
			} else {
				String errands = FileUtils.readFileToString(file);

				JSONObject allErrandsJsonOriginal = new JSONObject(errands);

				if (allErrandsJsonOriginal.has("daysToPostpone")) {
					daysToPostponeSaved = allErrandsJsonOriginal
							.getInt("daysToPostpone");
				} else {
					daysToPostponeSaved = DAYS_TO_POSTPONE;
				}
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

				messages.put(StringUtils.capitalize(printedTitle),
						messageMetadata);
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
			theImapClient.close();
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
	
	private static class Postpone {

		private static final String MESSAGE_ID = "Message-ID";

		private static final String DIR_PATH = "/home/sarnobat/.gcal_task_warrior";
		private static final File mTasksFileLatest = new File(DIR_PATH
				+ "/tasks.json");
		private static final Calendar _service = getCalendarService();

		static void postponeTest(String itemNumber, String daysToPostponeString)
				throws IOException, NoSuchProviderException, MessagingException,
				GeneralSecurityException {
			System.out.println("Will postpone event "+itemNumber+" by " + daysToPostponeString
					+ " days.");
			System.out.println("FYI - file contents at time of postpone are: " + FileUtils.readFileToString(mTasksFileLatest));
			JSONObject eventJson = getEventJsonFromResponse(itemNumber, mTasksFileLatest);
			String title = eventJson.getString("title");
			System.out.println("Title:\n\t" + title);
			Store theImapClient = connect();
			_1:{
				Set<Message> msgs = getMessages(theImapClient, title);
				for (Message msg : msgs) {
					System.out.println("Event ID\n\t" + getEventID(msg));
					System.out.println("Calendar name\n\t"
							+ getCalendarName(msg));
					System.out.println("Calendar ID:\n\t"
							+ getCalendarId(getCalendarName(msg)));
				}
			}
			if (theImapClient.isConnected()) {
				theImapClient.close();
			}
		}
		
		static void postpone(String itemNumber, String daysToPostponeString)
				throws IOException, NoSuchProviderException, MessagingException,
				GeneralSecurityException {
			System.out.println("Will postpone event "+itemNumber+" by " + daysToPostponeString
					+ " days.");
			System.out.println("FYI - file contents at time of postpone are: " + FileUtils.readFileToString(mTasksFileLatest));
			JSONObject eventJson = getEventJsonFromResponse(itemNumber, mTasksFileLatest);
			String title = eventJson.getString("title");
			System.out.println("Title:\n\t" + title);
			Store theImapClient = connect();
			_1:{
				Set<Message> msgs = getMessages(theImapClient, title);
				for (Message msg : msgs) {
					System.out.println("Event ID\n\t" + getEventID(msg));
					System.out.println("Calendar name\n\t"
							+ getCalendarName(msg));
					System.out.println("Calendar ID:\n\t"
							+ getCalendarId(getCalendarName(msg)));
					CalendarRequest<Event> calendarAction = createPostponeTask(
							daysToPostponeString, title, msg);
					commitPostpone(theImapClient, calendarAction, getMessageID(msg));
				}
			}
			if (theImapClient.isConnected()) {
				theImapClient.close();
			}
		}

		private static Set<Message> getMessages(Store theImapClient, String title)
				throws NoSuchProviderException, MessagingException {
			Message[] msgs = getMessages(theImapClient);
			ArrayList<Message> theMsgList = new ArrayList<Message>();
			System.out.println("Delete.getMessages() - looking for " + title);
			for (Message aMsg : msgs) {
				if (aMsg.getSubject().equals(title)) {
					theMsgList.add(checkNotNull(aMsg));
					System.out.println("Delete.getMessages() - matched: " + aMsg.getSubject());
				} else {
					System.out.println("Delete.getMessages() - No match: " + aMsg.getSubject());
				}
			}
			if (theMsgList.size() == 0) {
				throw new RuntimeException();
			}
			return ImmutableSet.copyOf(theMsgList);
		}

		
//		@Deprecated
//		private static Set<Message> getMessages(String title)
//				throws NoSuchProviderException, MessagingException {
//			Message[] msgs = getMessages();
//			ArrayList<Message> theMsgList = new ArrayList<Message>();
//			System.out.println("Delete.getMessages() - looking for " + title);
//			for (Message aMsg : msgs) {
//				if (aMsg.getSubject().equals(title)) {
//					theMsgList.add(checkNotNull(aMsg));
//					System.out.println("Delete.getMessages() - matched: " + aMsg.getSubject());
//				} else {
//					System.out.println("Delete.getMessages() - No match: " + aMsg.getSubject());
//				}
//			}
//			if (theMsgList.size() == 0) {
//				throw new RuntimeException();
//			}
//			return ImmutableSet.copyOf(theMsgList);
//		}

		
		private static CalendarRequest<Event> createPostponeTask(
				String daysToPostponeString, String title, Message msg)
				throws GeneralSecurityException, IOException,
				MessagingException {
			CalendarRequest<Event> calendarAction;
			try {
				calendarAction = createUpdateTask(getCalendarName(msg),
						getCalendarId(getCalendarName(msg)),
						getEventID(msg), daysToPostponeString);
			} catch (IsRecurringEventException e) {

				calendarAction = createInsertTask(daysToPostponeString,
						title);
			}
			return calendarAction;
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

			final String string = "/home/sarnobat/.gcal_task_warrior";
			final File file = new File(string + "/calendars.json");
			String s;
			try {
				s = FileUtils.readFileToString(file, "UTF-8");
				JSONObject j = new JSONObject(s);
				System.out.println("Calendar count: " + j.length());
				if ("Sridhar Sarnobat".equals(calendarName)) {
					calendarName = "ss401533@gmail.com";
				}
				JSONObject jsonObject = (JSONObject) j.get(calendarName);
				String id = jsonObject.getString("calendar_id");
				return id;
			} catch (IOException e) {
				e.printStackTrace();
			}
			return null;
		}

		private static Message getMessage(Store theImapClient ,String title)
				throws NoSuchProviderException, MessagingException {
			Message[] msgs = getMessages(theImapClient );
			Message msg = null;
			for (Message aMsg : msgs) {
				if (aMsg.getSubject().equals(title)) {
					msg = aMsg;
					break;
				}
			}
			if (msg == null) {
				throw new RuntimeException("Can't find email with subject title: " + title);
			}
			return msg;
		}

		private static void commitPostpone(Store theImapClient,final CalendarRequest<Event> update,
				final String messageIdToDelete) throws NoSuchProviderException,
				MessagingException, IOException {

System.out.println("commitPostpone() - begin" + messageIdToDelete);
			// All persistent changes are done right at the end, so that any
			// exceptions can get thrown first.
			deleteEmail(messageIdToDelete, theImapClient);

			executeCalendarRequest(update);
System.out.println("commitPostpone() - end " + messageIdToDelete);
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

		private static void deleteEmail(
				final String messageIdToDelete, Store theImapClient ) {
				try {
						
						deleteEmail(theImapClient ,messageIdToDelete);
					} catch (NoSuchProviderException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (MessagingException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				
		}
		
		private static void deleteEmailInSeparateThread(
				final String messageIdToDelete) {
			new Thread() {
				@Override
				public void run() {
					try {
						Store theImapClient = connect();
						deleteEmail(theImapClient ,messageIdToDelete);
						theImapClient.close();
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
		private static JSONObject getEventJsonFromResponse(String itemToDelete,
				File tasksFileLastDisplayed) throws IOException {
			String errands = FileUtils.readFileToString(tasksFileLastDisplayed);
			JSONObject eventJson = getEventJsonFromResponseHelper(itemToDelete, errands);
			return eventJson;
		}

		private static JSONObject getEventJsonFromResponseHelper(String itemToDelete, String errands) {
			JSONObject jsonObject = new JSONObject(errands);
			JSONObject allErrandsJson = jsonObject.getJSONObject("tasks");
			String allErrands = allErrandsJson.toString();
			return getEventJsonFromFile(itemToDelete, allErrands);
		}

		/** Inline this into {@link NotNow#getEventJsonFromResponseHelper} */
		@Deprecated 
		private static JSONObject getEventJsonFromFile(String itemToDelete,
				String allErrands) {
			JSONObject eventJson = (new JSONObject(allErrands))
					.getJSONObject(itemToDelete);
			return eventJson;
		}

		private static void deleteEmail(Store theImapClient ,String messageIdToDelete)
				throws NoSuchProviderException, MessagingException {
			Message[] messages;
			messages = getMessages(theImapClient);
			for (Message aMessage : messages) {
				String aMessageID = getMessageID(aMessage);
				if (aMessageID.equals(messageIdToDelete)) {
					aMessage.setFlag(Flags.Flag.DELETED, true);
					System.out.println("Deleted email:\t" + aMessage.getSubject());
					break;
				}
			}
			System.out.println("----------------------------");
		}

		// Useful
		private static Update createUpdateTask(String calendarName,
				String calendarId, String eventID, String daysToPostponeString)
				throws GeneralSecurityException, IOException,
				IsRecurringEventException {
			int daysToPostpone = Integer.parseInt(daysToPostponeString);
			// Get event's current time

			//thisDoesNothing(calendarName);
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
			if ("Sridhar Sarnobat".equals(calendarName)) {
				calendarName = "ss401533@gmail.com";
			}
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

		private static Message[] getMessages(Store theImapClient) throws NoSuchProviderException,
				MessagingException {
//			Store theImapClient = connect();
			Folder folder = openUrgentFolder(theImapClient);

			Message[] msgs = folder.getMessages();

			FetchProfile fp = new FetchProfile();
			fp.add(FetchProfile.Item.ENVELOPE);
			fp.add("X-mailer");
			folder.fetch(msgs, fp);
//			theImapClient.close();
			return msgs;
		}

		private static Folder openUrgentFolder(Store theImapClient)
				throws MessagingException {
			Folder folder = theImapClient
					.getFolder("3 - Urg - time sensitive - this week");
			folder.open(Folder.READ_WRITE);
			return folder;
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


	public static void main(String[] args) throws URISyntaxException, NoSuchProviderException, MessagingException, IOException {
		new Thread() {
			public void run() {
				try {
					ListDisplaySynchronous.getErrands();
				} catch (NoSuchProviderException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (MessagingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}.start();
		ListDisplaySynchronous.writeCalendarsToFileInSeparateThread();
		try {
			HttpServer server = JdkHttpServerFactory.createHttpServer(new URI(
					"http://localhost:4456/"), new ResourceConfig(
					HelloWorldResource.class));
		} catch (Exception e) {
			System.err.println(e.getMessage());
			System.err.println("Port in use. Not starting new instance.");
		}
	}
}
